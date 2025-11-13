package com.xzll.client.protobuf.interactive;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.ProtoConverterUtil;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

/**
 * @Author: hzz
 * @Date: 2025/10/29
 * @Description: 交互式客户端处理器
 */
public class InteractiveClientHandler extends SimpleChannelInboundHandler<Object> {

    public static final String IP = "127.0.0.1";
    public static final String PORT = "8083";


//    public static final String IP = "120.46.85.43";
//    public static final String PORT = "80";

    private final WebSocketClientHandshaker handshaker;
    private final String userId;
    private ChannelPromise handshakeFuture;
    
    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    
    // 存储待处理的好友请求 <requestId, FriendRequestPush>
    private final Map<String, FriendRequestPush> pendingFriendRequests = new ConcurrentHashMap<>();
    
    // 存储已发送消息的客户端ID，用于匹配ACK（clientMsgId -> 发送时间）
    private final Map<String, Long> sentMessages = new ConcurrentHashMap<>();
    
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
                    handleClientAck(protoResponse);
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
                
                default:
                    System.out.println("[" + getTime() + "] ❓ 收到未知类型消息: " + msgType);
            }
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理单聊消息（优化版：适配 fixed64/bytes）
     */
    private void handleC2CMessage(ImProtoResponse protoResponse) {
        try {
            C2CMsgPush pushMsg = C2CMsgPush.parseFrom(protoResponse.getPayload());
            
            // 类型转换：fixed64 -> String, bytes -> String
            String clientMsgId = ProtoConverterUtil.bytesToUuidString(pushMsg.getClientMsgId());
            String msgId = ProtoConverterUtil.longToSnowflakeString(pushMsg.getMsgId());
            String from = ProtoConverterUtil.longToSnowflakeString(pushMsg.getFrom());
            String to = ProtoConverterUtil.longToSnowflakeString(pushMsg.getTo());
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║              📨 收到新消息（优化版）                 ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  时间: " + getTime());
            System.out.println("║  发送方: " + from);
            System.out.println("║  客户端ID: " + clientMsgId);
            System.out.println("║  服务端ID: " + msgId);
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
     * 处理服务端ACK（双轨制优化版：适配 fixed64/bytes，移除ackTextDesc）
     * 注意：此方法现在已被handleClientAck方法统一处理，保留此方法以供参考
     */
    private void handleServerAck(ImProtoResponse protoResponse) {
        try {
            ServerAckPush serverAck = ServerAckPush.parseFrom(protoResponse.getPayload());
            
            // 类型转换：fixed64 -> String, bytes -> String
            String clientMsgId = ProtoConverterUtil.bytesToUuidString(serverAck.getClientMsgId());
            String msgId = ProtoConverterUtil.longToSnowflakeString(serverAck.getMsgId());
            
            // 从已发送消息中查找对应的消息
            Long sendTime = sentMessages.get(clientMsgId);
            String timeInfo = sendTime != null ? 
                String.format(" (耗时: %dms)", System.currentTimeMillis() - sendTime) : "";
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║          💡 ★★★ 收到ACK（双轨制优化版）★★★        ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  客户端ID: " + clientMsgId);
            System.out.println("║  服务端ID: " + msgId);
            System.out.println("║  状态: SERVER_RECEIVED" + timeInfo);
            System.out.println("╚════════════════════════════════════════════════════╝");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析服务端ACK失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理ACK消息（统一处理ServerAck和ClientAck）（优化版：适配 fixed64/bytes）
     * 注意：ServerAck(status=1)和ClientAck(status=3/4)都通过C2C_ACK发送
     */
    private void handleClientAck(ImProtoResponse protoResponse) {
        try {
            C2CAckReq ackReq = C2CAckReq.parseFrom(protoResponse.getPayload());
            
            // 类型转换：fixed64 -> String, bytes -> String
            String clientMsgId = ProtoConverterUtil.bytesToUuidString(ackReq.getClientMsgId());
            String msgId = ProtoConverterUtil.longToSnowflakeString(ackReq.getMsgId());
            
            String statusText;
            String emoji;
            
            // 判断是ServerAck还是ClientAck
            if (ackReq.getStatus() == 1) {
                // ✅ ServerAck：服务端已接收
                statusText = "服务端已接收";
                emoji = "💡";
                
                // 从已发送消息中查找对应的消息，计算耗时
                Long sendTime = sentMessages.get(clientMsgId);
                String timeInfo = sendTime != null ? 
                    String.format(" (耗时: %dms)", System.currentTimeMillis() - sendTime) : "";
                
                System.out.println();
                System.out.println("╔════════════════════════════════════════════════════╗");
                System.out.println("║          💡 ★★★ 收到ServerAck（双轨制优化版）★★★   ║");
                System.out.println("╠════════════════════════════════════════════════════╣");
                System.out.println("║  客户端ID: " + clientMsgId);
                System.out.println("║  服务端ID: " + msgId);
                System.out.println("║  状态: SERVER_RECEIVED" + timeInfo);
                System.out.println("║  时间: " + getTime());
                System.out.println("╚════════════════════════════════════════════════════╝");
                
                // 清理已发送消息记录（可选，避免内存泄漏）
                sentMessages.remove(clientMsgId);
                return;
            }
            
            // ClientAck：对方未读/已读
            switch (ackReq.getStatus()) {
                case 3:
                    statusText = "对方未读";
                    emoji = "📬";
                    break;
                case 4:
                    statusText = "对方已读";
                    emoji = "✅";
                    break;
                default:
                    statusText = "未知状态(" + ackReq.getStatus() + ")";
                    emoji = "❓";
            }
            
            System.out.println("[" + getTime() + "] " + emoji + " 客户端ACK: " + statusText + 
                             " (clientId: " + clientMsgId + ", msgId: " + msgId + ")");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析ACK失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理撤回消息（优化版：适配 fixed64）
     */
    private void handleWithdrawMessage(ImProtoResponse protoResponse) {
        try {
            C2CWithdrawReq withdraw = C2CWithdrawReq.parseFrom(protoResponse.getPayload());
            
            // 类型转换：fixed64 -> String
            String msgId = ProtoConverterUtil.longToSnowflakeString(withdraw.getMsgId());
            String from = ProtoConverterUtil.longToSnowflakeString(withdraw.getFrom());
            
            System.out.println();
            System.out.println("[" + getTime() + "] 🔄 收到撤回通知（优化版）");
            System.out.println("  消息ID: " + msgId);
            System.out.println("  发起人: " + from);
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析撤回消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理好友请求（优化版：适配 fixed64）
     */
    private void handleFriendRequest(ImProtoResponse protoResponse) {
        try {
            FriendRequestPush request = FriendRequestPush.parseFrom(protoResponse.getPayload());
            
            // 类型转换：fixed64 -> String
            String toUserId = ProtoConverterUtil.longToSnowflakeString(request.getToUserId());
            String requestId = ProtoConverterUtil.longToSnowflakeString(request.getRequestId());
            String fromUserId = ProtoConverterUtil.longToSnowflakeString(request.getFromUserId());
            
            // 保存待处理的好友请求（使用转换后的requestId）
            pendingFriendRequests.put(requestId, request);
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║              👥 收到好友请求（优化版）              ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  申请人: " + request.getFromUserName() + " (" + fromUserId + ")");
            System.out.println("║  申请消息: " + request.getRequestMessage());
            System.out.println("║  请求ID: " + requestId);
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  💡 处理方式:                                      ║");
            System.out.println("║     同意: friend accept " + requestId);
            System.out.println("║     拒绝: friend reject " + requestId);
            System.out.println("║     查看: friend list                              ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析好友请求失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理好友响应（优化版：适配 fixed64）
     */
    private void handleFriendResponse(ImProtoResponse protoResponse) {
        try {
            FriendResponsePush response = FriendResponsePush.parseFrom(protoResponse.getPayload());
            
            // 类型转换：fixed64 -> String
            String toUserId = ProtoConverterUtil.longToSnowflakeString(response.getToUserId());
            String requestId = ProtoConverterUtil.longToSnowflakeString(response.getRequestId());
            String fromUserId = ProtoConverterUtil.longToSnowflakeString(response.getFromUserId());
            
            String resultText = response.getStatus() == 1 ? "✅ 已同意" : "❌ 已拒绝";
            String emoji = response.getStatus() == 1 ? "🎉" : "😔";
            
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║            👥 好友申请响应（优化版）                ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  响应人: " + response.getFromUserName());
            System.out.println("║  请求ID: " + requestId);
            System.out.println("║  结果: " + resultText);
            System.out.println("║  " + emoji + " " + response.getPushContent());
            System.out.println("╚════════════════════════════════════════════════════╝");
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析好友响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送文本消息（双轨制优化版：适配 fixed64/bytes，chatId不传）
     */
    public void sendTextMessage(String toUserId, String content) {
        try {
            // 生成客户端消息ID（UUID）
            String clientMsgId = UUID.randomUUID().toString();
            long sendTime = System.currentTimeMillis();
            
            // 记录已发送消息，用于后续ACK匹配
            sentMessages.put(clientMsgId, sendTime);
            
            // 构建 C2CSendReq（双轨制优化版：clientMsgId=bytes, msgId=0, from/to=fixed64, chatId不传）
            C2CSendReq sendReq = C2CSendReq.newBuilder()
                    .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(clientMsgId))  // UUID String -> bytes
                    .setMsgId(0L)  // 留空（0L），服务端会自动生成
                    .setFrom(ProtoConverterUtil.snowflakeStringToLong(userId))  // String -> fixed64
                    .setTo(ProtoConverterUtil.snowflakeStringToLong(toUserId))  // String -> fixed64
                    .setFormat(1) // 1=文本
                    .setContent(content)
                    .setTime(sendTime)  // fixed64
                    // chatId 已从proto删除，服务端会根据from+to动态生成
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
            
            System.out.println("[" + getTime() + "] 📤 消息已发送（优化版） (clientId: " + clientMsgId + ")");
            
        } catch (Exception e) {
            System.err.println("[" + getTime() + "] ❌ 发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送ACK（双轨制优化版：适配 fixed64/bytes，chatId不传）
     */
    private void sendAck(C2CMsgPush pushMsg, int status) {
        try {
            C2CAckReq ackReq = C2CAckReq.newBuilder()
                    .setClientMsgId(pushMsg.getClientMsgId()) // bytes（直接使用，无需转换）
                    .setMsgId(pushMsg.getMsgId())            // fixed64（直接使用，无需转换）
                    .setFrom(pushMsg.getTo())                // fixed64（直接使用，发送方和接收方对调）
                    .setTo(pushMsg.getFrom())                // fixed64（直接使用，发送方和接收方对调）
                    .setStatus(status)
                    // chatId 已从proto删除，服务端会动态生成
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

    public static String buildChatId(Integer bizType, String chatType, Long fromUserId, Long toUserId) {
        Assert.isTrue(StringUtils.isNotBlank(chatType) && Objects.nonNull(fromUserId) && Objects.nonNull(toUserId));
        bizType = bizType == null ? ImConstant.DEFAULT_BIZ_TYPE : bizType;
        return String.format("%d-%s-%s-%s", bizType, ImConstant.ChatType.CHAT_TYPE_MAP.get(chatType), fromUserId, toUserId);
    }

    public static String buildC2CChatId(Integer bizType, Long fromUserId, Long toUserId) {
        //单聊时 第一个userId是小的 第二个userId是较大的
        Long smallUserId = null;
        Long bigUserId = null;
        if (fromUserId < toUserId) {
            smallUserId = fromUserId;
            bigUserId = toUserId;
        } else {
            smallUserId = toUserId;
            bigUserId = fromUserId;
        }
        return buildChatId(bizType, ImConstant.ChatType.C2C, smallUserId, bigUserId);
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
     * 处理好友请求（同意或拒绝）（优化版：适配 fixed64）
     */
    public void handleFriendRequestAction(String requestId, int handleResult) {
        FriendRequestPush request = pendingFriendRequests.get(requestId);
        
        if (request == null) {
            System.err.println("[" + getTime() + "] ❌ 未找到请求ID: " + requestId);
            System.out.println("💡 提示: 使用 'friend list' 查看所有待处理的请求");
            return;
        }
        
        try {
            System.out.println("[" + getTime() + "] ⏳ 正在处理好友请求（优化版）...");
            
            // 构建处理请求参数（参考client2实现）
            // 注意：HTTP接口期望的是String类型的ID
            JSONObject handleRequest = new JSONObject();
            handleRequest.put("requestId", requestId); // 已经是转换后的String
            handleRequest.put("userId", ProtoConverterUtil.longToSnowflakeString(request.getToUserId())); // fixed64 -> String
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
     * 列出所有待处理的好友请求（优化版：适配 fixed64）
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
        System.out.println("║            待处理的好友请求列表（优化版）           ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        int index = 1;
        for (Map.Entry<String, FriendRequestPush> entry : pendingFriendRequests.entrySet()) {
            FriendRequestPush request = entry.getValue();
            String requestId = entry.getKey(); // 使用Map的key（已转换的String）
            String fromUserId = ProtoConverterUtil.longToSnowflakeString(request.getFromUserId());
            
            System.out.println("║");
            System.out.println("║  [" + index + "] 申请人: " + request.getFromUserName() + 
                             " (" + fromUserId + ")");
            System.out.println("║      消息: " + request.getRequestMessage());
            System.out.println("║      请求ID: " + requestId);
            System.out.println("║      同意: friend accept " + requestId);
            System.out.println("║      拒绝: friend reject " + requestId);
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
    
}

