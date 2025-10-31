package com.xzll.client.protobuf.interactive;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.grpc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.CollectionUtils;

/**
 * @Author: hzz
 * @Date: 2025/10/29
 * @Description: 交互式客户端处理器
 */
public class InteractiveClientHandler extends SimpleChannelInboundHandler<Object> {

//    public static final String IP = "127.0.0.1";
//    public static final String PORT = "8083";


        public static final String IP = "120.46.85.43";
    public static final String PORT = "80";

    private final WebSocketClientHandshaker handshaker;
    private final String userId;
    private ChannelPromise handshakeFuture;
    
    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    
    // 存储待处理的好友请求 <requestId, FriendRequestPush>
    private final Map<String, FriendRequestPush> pendingFriendRequests = new ConcurrentHashMap<>();
    
    // 存储从服务端获取的消息ID
    private static final List<String> msgIds = new ArrayList<>();
    
    // 标识是否正在获取消息ID
    private static volatile boolean getMsgFlag = false;
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss");
    
    public InteractiveClientHandler(WebSocketClientHandshaker handshaker, String userId) {
        this.handshaker = handshaker;
        this.userId = userId;
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
        handshaker.handshake(ctx.channel());
        super.channelActive(ctx);
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 发送心跳
                System.out.println("[" + getTime() + "] 💓 发送心跳");
                ctx.writeAndFlush(new PingWebSocketFrame());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            System.out.println("[" + getTime() + "] WebSocket 握手完成");
            handshakeFuture.setSuccess();
            return;
        }
        
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                "Unexpected FullHttpResponse (status=" + response.status() + 
                ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')'
            );
        }
        
        WebSocketFrame frame = (WebSocketFrame) msg;
        
        if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryMessage(ctx, (BinaryWebSocketFrame) frame);
        } else if (frame instanceof PongWebSocketFrame) {
            // 心跳响应
            System.out.println("[" + getTime() + "] 💓 收到心跳响应");
        } else if (frame instanceof CloseWebSocketFrame) {
            System.out.println("[" + getTime() + "] ❌ 连接已关闭");
            ch.close();
        }
    }
    
    /**
     * 处理二进制消息（Protobuf）
     */
    private void handleBinaryMessage(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        try {
            ByteBuf content = frame.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            
            ImProtoResponse protoResponse = ImProtoResponse.parseFrom(bytes);
            MsgType msgType = protoResponse.getType();
            
            receivedCount.incrementAndGet();
            
            switch (msgType) {
                case C2C_MSG_PUSH:
                    handleC2CMessage(protoResponse);
                    break;
                
                case C2C_ACK:
                    handleAckMessage(protoResponse);
                    break;
                
                case C2C_WITHDRAW:
                    handleWithdrawMessage(protoResponse);
                    break;
                
                case FRIEND_REQUEST:
                    handleFriendRequest(protoResponse);
                    break;
                
                case FRIEND_RESPONSE:
                    handleFriendResponse(protoResponse);
                    break;
                
                case PUSH_BATCH_MSG_IDS:
                    handleBatchMsgIds(protoResponse);
                    break;
                
                default:
                    System.out.println("[" + getTime() + "] ❓ 收到未知类型消息: " + msgType);
            }
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理单聊消息
     */
    private void handleC2CMessage(ImProtoResponse protoResponse) {
        try {
            C2CMsgPush pushMsg = C2CMsgPush.parseFrom(protoResponse.getPayload());
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║              📨 收到新消息                          ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  时间: " + getTime());
            System.out.println("║  发送方: " + pushMsg.getFrom());
            System.out.println("║  消息ID: " + pushMsg.getMsgId());
            System.out.println("║  内容: " + pushMsg.getContent());
            System.out.println("╚════════════════════════════════════════════════════╝");
            
            // 自动回复ACK（未读）
            sendAck(pushMsg, 3);
            
            // 延迟回复ACK（已读）
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    sendAck(pushMsg, 4);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析单聊消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理ACK消息
     */
    private void handleAckMessage(ImProtoResponse protoResponse) {
        try {
            C2CAckReq ack = C2CAckReq.parseFrom(protoResponse.getPayload());
            
            String statusText;
            String emoji;
            switch (ack.getStatus()) {
                case 1:
                    statusText = "服务器已接收";
                    emoji = "📡";
                    break;
                case 3:
                    statusText = "对方未读";
                    emoji = "📬";
                    break;
                case 4:
                    statusText = "对方已读";
                    emoji = "✅";
                    break;
                default:
                    statusText = "未知状态(" + ack.getStatus() + ")";
                    emoji = "❓";
            }
            
            System.out.println("[" + getTime() + "] " + emoji + " ACK: " + statusText + 
                             " (msgId: " + ack.getMsgId() + ")");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析ACK失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理撤回消息
     */
    private void handleWithdrawMessage(ImProtoResponse protoResponse) {
        try {
            C2CWithdrawReq withdraw = C2CWithdrawReq.parseFrom(protoResponse.getPayload());
            
            System.out.println();
            System.out.println("[" + getTime() + "] 🔄 收到撤回通知");
            System.out.println("  消息ID: " + withdraw.getMsgId());
            System.out.println("  发起人: " + withdraw.getFrom());
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析撤回消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理好友请求
     */
    private void handleFriendRequest(ImProtoResponse protoResponse) {
        try {
            FriendRequestPush request = FriendRequestPush.parseFrom(protoResponse.getPayload());
            
            // 保存待处理的好友请求
            pendingFriendRequests.put(request.getRequestId(), request);
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║              👥 收到好友请求                        ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  申请人: " + request.getFromUserName() + " (" + request.getFromUserId() + ")");
            System.out.println("║  申请消息: " + request.getRequestMessage());
            System.out.println("║  请求ID: " + request.getRequestId());
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  💡 处理方式:                                      ║");
            System.out.println("║     同意: friend accept " + request.getRequestId());
            System.out.println("║     拒绝: friend reject " + request.getRequestId());
            System.out.println("║     查看: friend list                              ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析好友请求失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理好友响应
     */
    private void handleFriendResponse(ImProtoResponse protoResponse) {
        try {
            FriendResponsePush response = FriendResponsePush.parseFrom(protoResponse.getPayload());
            
            String resultText = response.getStatus() == 1 ? "✅ 已同意" : "❌ 已拒绝";
            String emoji = response.getStatus() == 1 ? "🎉" : "😔";
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║            👥 好友申请响应                          ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  响应人: " + response.getFromUserName());
            System.out.println("║  结果: " + resultText);
            System.out.println("║  " + emoji + " " + response.getPushContent());
            System.out.println("╚════════════════════════════════════════════════════╝");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析好友响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送文本消息
     */
    public void sendTextMessage(String toUserId, String content) {
        try {
            // 检查是否有可用的msgId
            if (getMsgFlag) {
                System.out.println("[" + getTime() + "] ⏳ 正在获取消息ID，请稍候...");
                return;
            }
            
            if (CollectionUtils.isEmpty(msgIds)) {
                System.out.println("[" + getTime() + "] 📥 消息ID为空，正在获取...");
                getMsgIds();
                getMsgFlag = true;
                return;
            }
            
            // 从集合中取出一个msgId
            String msgId;
            synchronized (msgIds) {
                if (msgIds.isEmpty()) {
                    System.out.println("[" + getTime() + "] ❌ 消息ID已用完，请重新获取");
                    return;
                }
                msgId = msgIds.remove(0);
            }
            
            String chatId = generateChatId(userId, toUserId);
            
            // 构建 C2CSendReq
            C2CSendReq sendReq = C2CSendReq.newBuilder()
                    .setMsgId(msgId)
                    .setFrom(userId)
                    .setTo(toUserId)
                    .setFormat(1) // 1=文本
                    .setContent(content)
                    .setTime(System.currentTimeMillis())
                    .setChatId(chatId)
                    .build();
            
            // 包装为 ImProtoRequest
            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
                    .setType(MsgType.C2C_SEND)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(sendReq.toByteArray()))
                    .build();
            
            // 发送
            byte[] bytes = protoRequest.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            handshakeFuture.channel().writeAndFlush(new BinaryWebSocketFrame(buf));
            
            sentCount.incrementAndGet();
            
        } catch (Exception e) {
            System.err.println("[" + getTime() + "] ❌ 发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送ACK
     */
    private void sendAck(C2CMsgPush pushMsg, int status) {
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
            handshakeFuture.channel().writeAndFlush(new BinaryWebSocketFrame(buf));
            
        } catch (Exception e) {
            System.err.println("[" + getTime() + "] ❌ 发送ACK失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成会话ID
     */
    private String generateChatId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }
    
    /**
     * 获取当前时间
     */
    private String getTime() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }
    
    public int getSentCount() {
        return sentCount.get();
    }
    
    public int getReceivedCount() {
        return receivedCount.get();
    }
    
    /**
     * 处理好友请求（同意或拒绝）
     */
    public void handleFriendRequestAction(String requestId, int handleResult) {
        FriendRequestPush request = pendingFriendRequests.get(requestId);
        
        if (request == null) {
            System.err.println("[" + getTime() + "] ❌ 未找到请求ID: " + requestId);
            System.out.println("💡 提示: 使用 'friend list' 查看所有待处理的请求");
            return;
        }
        
        try {
            System.out.println("[" + getTime() + "] ⏳ 正在处理好友请求...");
            
            // 构建处理请求参数（参考client2实现）
            JSONObject handleRequest = new JSONObject();
            handleRequest.put("requestId", request.getRequestId());
            handleRequest.put("userId", request.getToUserId());
            handleRequest.put("handleResult", handleResult); // 1=同意, 2=拒绝
            
            // 调用HTTP接口处理好友申请
            String result = sendHttpPost("http://" + "120.46.85.43" + ":" + "80" + "/im-business/api/friend/request/handle",
                                       handleRequest.toJSONString());

//            String result = sendHttpPost("http://" + "192.168.1.150" + ":" + "8083" + "/api/friend/request/handle",
//                    handleRequest.toJSONString());

            
            // 处理成功，从待处理列表中移除
            pendingFriendRequests.remove(requestId);
            
            String action = handleResult == 1 ? "同意" : "拒绝";
            System.out.println("[" + getTime() + "] ✅ 好友请求处理成功！");
            System.out.println("   操作: " + action + " " + request.getFromUserName() + " 的好友申请");
            System.out.println("   响应: " + result);
            
        } catch (Exception e) {
            System.err.println("[" + getTime() + "] ❌ 处理好友请求失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 列出所有待处理的好友请求
     */
    public void listPendingFriendRequests() {
        if (pendingFriendRequests.isEmpty()) {
            System.out.println();
            System.out.println("┌─────────────────────────────────────┐");
            System.out.println("│      暂无待处理的好友请求           │");
            System.out.println("└─────────────────────────────────────┘");
            return;
        }
        
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║            待处理的好友请求列表                     ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        int index = 1;
        for (Map.Entry<String, FriendRequestPush> entry : pendingFriendRequests.entrySet()) {
            FriendRequestPush request = entry.getValue();
            System.out.println("║");
            System.out.println("║  [" + index + "] 申请人: " + request.getFromUserName() + 
                             " (" + request.getFromUserId() + ")");
            System.out.println("║      消息: " + request.getRequestMessage());
            System.out.println("║      请求ID: " + request.getRequestId());
            System.out.println("║      同意: friend accept " + request.getRequestId());
            System.out.println("║      拒绝: friend reject " + request.getRequestId());
            index++;
        }
        
        System.out.println("╚════════════════════════════════════════════════════╝");
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
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
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
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[" + getTime() + "] ❌ 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[" + getTime() + "] ❌ 连接已断开");
        super.channelInactive(ctx);
    }
    
    /**
     * 获取消息ID列表
     */
    private void getMsgIds() {
        try {
            // 构建获取消息ID请求
            GetBatchMsgIdsReq getBatchMsgIdsReq = GetBatchMsgIdsReq.newBuilder()
                    .setUserId(userId)
                    .build();

            // 包装为 ImProtoRequest
            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
                    .setType(MsgType.GET_BATCH_MSG_IDS)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(getBatchMsgIdsReq.toByteArray()))
                    .build();

            // 发送 Protobuf 二进制消息
            byte[] bytes = protoRequest.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(buf);
            handshakeFuture.channel().writeAndFlush(binaryFrame);
            System.out.println("[" + getTime() + "] 📤 发送获取消息ID请求");
        } catch (Exception e) {
            System.err.println("[" + getTime() + "] ❌ 获取消息ID失败: " + e.getMessage());
            getMsgFlag = false; // 重置标志位
        }
    }
    
    /**
     * 处理批量消息ID
     */
    private void handleBatchMsgIds(ImProtoResponse protoResponse) {
        try {
            BatchMsgIdsPush resp = BatchMsgIdsPush.parseFrom(protoResponse.getPayload());
            List<String> msgIdList = resp.getMsgIdsList();
            
            System.out.println("[" + getTime() + "] 📨 获取到一批消息ID，数量: " + msgIdList.size());
            
            if (!CollectionUtils.isEmpty(msgIdList)) {
                synchronized (msgIds) {
                    msgIds.addAll(msgIdList);
                }
                getMsgFlag = false; // 重置标志位
                System.out.println("[" + getTime() + "] ✅ 消息ID已添加到本地缓存，当前缓存数量: " + msgIds.size());
            } else {
                getMsgFlag = false; // 重置标志位
                System.out.println("[" + getTime() + "] ⚠️ 获取到的消息ID列表为空");
            }
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析 BatchMsgIdsPush 失败: " + e.getMessage());
            getMsgFlag = false; // 重置标志位
        }
    }
}

