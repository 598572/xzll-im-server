package com.xzll.client.protobuf.clien2;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.grpc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProtobufWebsocketClientHandler2 extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    public ProtobufWebsocketClientHandler2(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[user2] 连接建立, uid: " + ctx.channel().attr(ImConstant.USER_ID_KEY).get());
        handshaker.handshake(ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.writeAndFlush(new PingWebSocketFrame());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            System.out.println("[user2] WebSocket connected!");
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException("Unexpected FullHttpResponse (getStatus=" + response.getStatus() + ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf content = ((BinaryWebSocketFrame) frame).content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            ImProtoResponse protoResponse = ImProtoResponse.parseFrom(bytes);
            MsgType msgType = protoResponse.getType();
            if (msgType == MsgType.C2C_MSG_PUSH) {
                try {
                    C2CMsgPush pushMsg = C2CMsgPush.parseFrom(protoResponse.getPayload());
                    System.out.println("============================================");
                    System.out.println("[user2] 收到单聊消息推送:");
                    System.out.println("  from=" + pushMsg.getFrom());
                    System.out.println("  msgId=" + pushMsg.getMsgId());
                    System.out.println("  content=" + pushMsg.getContent());
                    System.out.println("============================================");
                    // 自动回复未读+已读 ACK
                    sendAck(ctx, pushMsg, 3);
                    Thread.sleep(500); // 模拟阅读延迟
                    sendAck(ctx, pushMsg, 4);
                } catch (Exception e) {
                    System.err.println("[user2] 解析/处理消息失败: " + e.getMessage());
                }
            } else if (msgType == MsgType.PUSH_BATCH_MSG_IDS) {
                // 忽略
            } else if (msgType == MsgType.C2C_WITHDRAW) {
                try {
                    C2CWithdrawReq withdraw = C2CWithdrawReq.parseFrom(protoResponse.getPayload());
                    System.out.println("★★★ [user2] 收到撤回通知, msgId=" + withdraw.getMsgId() + 
                            ", from=" + withdraw.getFrom() + " ★★★");
                } catch (InvalidProtocolBufferException e) {
                    System.err.println("[user2] 解析 WITHDRAW 失败: " + e.getMessage());
                }
            } else if (msgType == MsgType.FRIEND_REQUEST) {
                // 处理好友请求
                handleFriendRequest(protoResponse);
            } else if (msgType == MsgType.FRIEND_RESPONSE) {
                // 处理好友响应
                handleFriendResponse(protoResponse);
            }
        } else if (frame instanceof CloseWebSocketFrame) {
            System.out.println("[user2] 连接关闭");
            ch.close();
        }
    }

    private void sendAck(ChannelHandlerContext ctx, C2CMsgPush pushMsg, int status) {
        try {
            C2CAckReq ackReq = C2CAckReq.newBuilder()
                    .setMsgId(pushMsg.getMsgId())
                    .setFrom(pushMsg.getTo())
                    .setTo(pushMsg.getFrom())
                    .setStatus(status)
                    .setChatId(pushMsg.getChatId())
                    .build();
            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
                    .setType(MsgType.C2C_ACK)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(ackReq.toByteArray()))
                    .build();
            byte[] bytes = protoRequest.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            ctx.channel().writeAndFlush(new BinaryWebSocketFrame(buf));
            System.out.println("[user2] 发送ACK完成: " + (status == 3 ? "未读" : "已读") + ", msgId=" + pushMsg.getMsgId());
        } catch (Exception e) {
            System.err.println("[user2] 发送ACK失败: " + e.getMessage());
        }
    }

    /**
     * 处理好友请求
     */
    private void handleFriendRequest(ImProtoResponse protoResponse) {
        try {
            // 解析好友请求数据
            FriendRequestPush request = FriendRequestPush.parseFrom(protoResponse.getPayload());
            
            System.out.println("============================================");
            System.out.println("📨 [user2] 收到好友请求:");
            System.out.println("  申请人: " + request.getFromUserName() + " (" + request.getFromUserId() + ")");
            System.out.println("  申请消息: " + request.getRequestMessage());
            System.out.println("  请求ID: " + request.getRequestId());
            System.out.println("  状态: " + getStatusText(request.getStatus()));
            System.out.println("  推送标题: " + request.getPushTitle());
            System.out.println("  推送内容: " + request.getPushContent());
            System.out.println("============================================");
            
            // 模拟用户操作：延迟2秒后自动同意好友请求
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 模拟用户思考时间
                    
                    System.out.println("💡 [user2] 准备处理好友请求...");
                    
                    // 构建处理请求参数
                    JSONObject handleRequest = new JSONObject();
                    handleRequest.put("requestId", request.getRequestId());
                    handleRequest.put("userId", request.getToUserId());
                    handleRequest.put("handleResult", 1); // 1=同意, 2=拒绝
                    
                    // 调用HTTP接口处理好友申请
                    String result = sendHttpPost("http://127.0.0.1:8083/api/friend/request/handle", 
                                                handleRequest.toJSONString());
                    
                    System.out.println("✅ [user2] 好友请求处理完成！");
                    System.out.println("   响应结果: " + result);
                    System.out.println("   已同意 " + request.getFromUserName() + " 的好友申请");
                    
                } catch (Exception e) {
                    System.err.println("❌ [user2] 处理好友请求失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("❌ [user2] 解析好友请求失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理好友响应（user2 作为申请人收到对方的响应）
     */
    private void handleFriendResponse(ImProtoResponse protoResponse) {
        try {
            FriendResponsePush response = FriendResponsePush.parseFrom(protoResponse.getPayload());
            
            System.out.println("============================================");
            System.out.println("📬 [user2] 收到好友申请响应:");
            System.out.println("  响应人: " + response.getFromUserName() + " (" + response.getFromUserId() + ")");
            System.out.println("  请求ID: " + response.getRequestId());
            
            if (response.getStatus() == 1) {
                System.out.println("  结果: ✅ 已同意");
                System.out.println("  🎉 恭喜！" + response.getFromUserName() + " 同意了你的好友申请");
            } else if (response.getStatus() == 2) {
                System.out.println("  结果: ❌ 已拒绝");
                System.out.println("  😔 " + response.getFromUserName() + " 拒绝了你的好友申请");
            } else {
                System.out.println("  结果: ❓ 未知状态(" + response.getStatus() + ")");
            }
            
            System.out.println("  推送标题: " + response.getPushTitle());
            System.out.println("  推送内容: " + response.getPushContent());
            System.out.println("============================================");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("❌ [user2] 解析好友响应失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送HTTP POST请求
     */
    private String sendHttpPost(String urlString, String jsonBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            // 设置请求方法和属性
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }
            
            // 读取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                br.close();
                return response.toString();
            } else {
                throw new Exception("HTTP请求失败，响应码: " + responseCode);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(int status) {
        switch (status) {
            case 0: return "待处理";
            case 1: return "已同意";
            case 2: return "已拒绝";
            case 3: return "已过期";
            default: return "未知(" + status + ")";
        }
    }
}


