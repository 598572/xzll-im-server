package com.xzll.client.protobuf.interactive;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.MsgFormatEnum;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群聊测试客户端Handler
 */
public class GroupChatClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private final String userId;
    private ChannelPromise handshakeFuture;

    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger receivedCount = new AtomicInteger(0);

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    public GroupChatClientHandler(WebSocketClientHandshaker handshaker, String userId) {
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[" + getTime() + "] ❌ 连接已断开");
        super.channelInactive(ctx);
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

        // 【调试日志】收到任何消息
        System.out.println("[" + getTime() + "] 🔍 [DEBUG] channelRead0 收到消息，类型: " + msg.getClass().getSimpleName());

        // 处理握手阶段
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

        // 处理WebSocket帧
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;

            // 处理关闭帧
            if (frame instanceof CloseWebSocketFrame) {
                System.out.println("[" + getTime() + "] ❌ 服务器关闭连接");
                ch.close();
                return;
            }

            // 处理Pong帧
            if (frame instanceof PongWebSocketFrame) {
                System.out.println("[" + getTime() + "] 💓 收到心跳响应");
                return;
            }

            // 处理二进制帧
            if (frame instanceof BinaryWebSocketFrame) {
                BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
                System.out.println("[" + getTime() + "] 🔍 [DEBUG] 收到二进制帧，开始处理");
                handleBinaryMessage(ctx, binaryFrame);
                return;
            }
        }

        // 【调试日志】未处理的消息类型
        System.out.println("[" + getTime() + "] ⚠️ [DEBUG] 未处理的消息类型: " + msg.getClass().getName());
    }

    /**
     * 处理二进制消息（Protobuf）
     */
    private void handleBinaryMessage(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        try {
            ByteBuf content = frame.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);

            System.out.println("[" + getTime() + "] 🔍 [DEBUG] 解析Protobuf，字节长度: " + bytes.length);

            ImProtoResponse protoResponse = ImProtoResponse.parseFrom(bytes);
            MsgType msgType = protoResponse.getType();

            System.out.println("[" + getTime() + "] 🔍 [DEBUG] 解析成功，消息类型: " + msgType + " (" + msgType.getNumber() + ")");

            receivedCount.incrementAndGet();

            switch (msgType) {
                case GROUP_MSG_PUSH:
                    System.out.println("[" + getTime() + "] ✅ [DEBUG] 进入 GROUP_MSG_PUSH 分支");
                    printGroupMessage(protoResponse);
                    break;
                case C2C_ACK:
                    System.out.println("[" + getTime() + "] ✅ [DEBUG] 进入 C2C_ACK 分支");
                    printC2CAckMessage(protoResponse);
                    break;
//                case GROUP_SEND_ACK:
//                    System.out.println("[" + getTime() + "] ✅ [DEBUG] 进入 GROUP_SEND_ACK 分支");
//                    printGroupSendAckMessage(protoResponse);
//                    break;
                default:
                    System.out.println("[" + getTime() + "] 📦 [DEBUG] 收到未知消息类型: " + msgType + " (" + msgType.getNumber() + ")");
                    break;
            }

        } catch (InvalidProtocolBufferException e) {
            System.err.println("[" + getTime() + "] ❌ 解析Protobuf消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印群聊消息
     */
    private void printGroupMessage(ImProtoResponse protoResponse) {
        try {
            GroupMsgPush groupMsg = GroupMsgPush.parseFrom(protoResponse.getPayload());

            String msgId = ProtoConverterUtil.longToSnowflakeString(groupMsg.getMsgId());
            String from = ProtoConverterUtil.longToSnowflakeString(groupMsg.getFrom());
            String groupId = ProtoConverterUtil.longToSnowflakeString(groupMsg.getGroupId());

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[" + getTime() + "] 👥 群聊消息");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("消息ID: " + msgId);
            System.out.println("群ID: " + groupId);
            System.out.println("群名: " + groupMsg.getGroupName());
            System.out.println("发送方: " + from);
            System.out.println("发送方昵称: " + groupMsg.getFromNickname());
            System.out.println("消息内容: " + groupMsg.getContent());
            System.out.println("消息格式: " + groupMsg.getFormat());
            System.out.println("消息时间: " + new java.util.Date(groupMsg.getTime()));
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            System.err.println("解析群聊消息失败: " + e.getMessage());
        }
    }

    /**
     * 打印C2C ACK
     */
    private void printC2CAckMessage(ImProtoResponse protoResponse) {
        try {
            C2CAckReq ack = C2CAckReq.parseFrom(protoResponse.getPayload());

            String clientMsgId = ProtoConverterUtil.bytesToUuidString(ack.getClientMsgId());
            String msgId = ProtoConverterUtil.longToSnowflakeString(ack.getMsgId());

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[" + getTime() + "] ✅ C2C消息已读ACK");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("ClientMsgId: " + clientMsgId);
            System.out.println("ServerMsgId: " + msgId);
            System.out.println("状态: " + ack.getStatus());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception e) {
            System.err.println("解析C2C ACK失败: " + e.getMessage());
        }
    }

    /**
     * 打印群聊发送ACK
     */
//    private void printGroupSendAckMessage(ImProtoResponse protoResponse) {
//        try {
//            GroupSendAck ack = GroupSendAck.parseFrom(protoResponse.getPayload());
//
//            String msgId = ProtoConverterUtil.longToSnowflakeString(ack.getMsgId());
//            String groupId = ProtoConverterUtil.longToSnowflakeString(ack.getGroupId());
//
//            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
//            System.out.println("[" + getTime() + "] ✅ 群聊消息发送ACK");
//            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
//            System.out.println("消息ID: " + msgId);
//            System.out.println("群ID: " + groupId);
//            System.out.println("状态: " + ack.getStatus());
//            System.out.println("失败原因: " + (ack.hasFailReason() ? ack.getFailReason() : "无"));
//            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
//        } catch (Exception e) {
//            System.err.println("解析群聊发送ACK失败: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }

    /**
     * 发送群聊消息
     */
    public void sendGroupMessage(Long groupId, String content) {
        try {
            Channel ch = handshakeFuture.channel();
            if (ch == null || !ch.isActive()) {
                System.err.println("[" + getTime() + "] ❌ 未连接到服务器");
                return;
            }

            long sendTime = System.currentTimeMillis();

            // 构建群聊消息
            GroupSendReq groupMsg = GroupSendReq.newBuilder()
                .setMsgId(0L)  // 留空，服务端会自动生成
                .setFrom(ProtoConverterUtil.snowflakeStringToLong(userId))
                .setGroupId(ProtoConverterUtil.snowflakeStringToLong(String.valueOf(groupId)))
                .setFormat(MsgFormatEnum.TEXT_MSG.getCode())
                .setContent(content)
                .setTime(sendTime)
                .build();

            // 构建请求
            ImProtoRequest request = ImProtoRequest.newBuilder()
                .setType(MsgType.GROUP_SEND)
                .setPayload(com.google.protobuf.ByteString.copyFrom(groupMsg.toByteArray()))
                .build();

            // 发送
            byte[] bytes = request.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            ch.writeAndFlush(new BinaryWebSocketFrame(buf));

            sentCount.incrementAndGet();

            System.out.println("[" + getTime() + "] 📤 发送群聊消息成功 - groupId:" + groupId + ", content:" + content);

        } catch (Exception e) {
            System.err.println("[" + getTime() + "] ❌ 发送群聊消息失败: " + e.getMessage());
        }
    }

    /**
     * 快速发送测试消息
     */
    public void quickSendGroupMessage(Long groupId) {
        String content = "测试消息 - " + System.currentTimeMillis();
        sendGroupMessage(groupId, content);
    }

    /**
     * 连续发送多条消息
     */
    public void multiSendGroupMessage(Long groupId, int count) {
        for (int i = 0; i < count; i++) {
            String content = "批量测试消息 #" + (i + 1) + " - " + System.currentTimeMillis();
            sendGroupMessage(groupId, content);

            // 间隔100ms
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[" + getTime() + "] 📤 批量发送完成 - count:" + count);
    }

    /**
     * 获取当前时间字符串
     */
    private String getTime() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    /**
     * 获取发送数量
     */
    public int getSentCount() {
        return sentCount.get();
    }

    /**
     * 获取接收数量
     */
    public int getReceivedCount() {
        return receivedCount.get();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[" + getTime() + "] ❌ 异常: " + cause.getMessage());
        cause.printStackTrace();
    }
}
