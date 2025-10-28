package com.xzll.client.protobuf.clien1;

import com.google.protobuf.InvalidProtocolBufferException;
 
import com.xzll.common.constant.ImConstant;
import com.xzll.grpc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
 
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2025/10/27
 * @Description: Protobuf 协议 WebSocket 客户端 Handler
 */
public class ProtobufWebsocketClientHandler1 extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    public static List<String> msgIds = new ArrayList<>();

    public ProtobufWebsocketClientHandler1(WebSocketClientHandshaker handshaker) {
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
        System.out.println("客户端连接建立,当前uid: " + ctx.channel().attr(ImConstant.USER_ID_KEY).get());
        // 在通道连接成功后发送握手连接
        handshaker.handshake(ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳消息
            sendHeartbeat(ctx);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private void sendHeartbeat(ChannelHandlerContext ctx) {
        // 构建心跳消息并发送
        PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame();
        ctx.writeAndFlush(pingWebSocketFrame);
        System.out.println("发送心跳 Ping");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();

        // 这里是第一次使用http连接成功的时候
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            System.out.println("WebSocket Client connected!");
            handshakeFuture.setSuccess();
            return;
        }

        // 这里是第一次使用http连接失败的时候
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.getStatus() + ", content=" + response.content()
                            .toString(CharsetUtil.UTF_8) + ')');
        }

        // 这里是服务器与客户端进行通讯的
        WebSocketFrame frame = (WebSocketFrame) msg;
        
        // 处理 Protobuf 二进制消息
        if (frame instanceof BinaryWebSocketFrame) {
            System.out.println("接收到 BinaryWebSocketFrame 消息");
            ByteBuf content = ((BinaryWebSocketFrame) frame).content();
            
            try {
                // 解析 ImProtoResponse
                byte[] bytes = new byte[content.readableBytes()];
                content.getBytes(content.readerIndex(), bytes);
                ImProtoResponse protoResponse = ImProtoResponse.parseFrom(bytes);
                
                System.out.println("收到 Protobuf 消息 - 类型: " + protoResponse.getType() + ", 响应码: " + protoResponse.getCode());

                // 根据消息类型处理
                handleProtoMessage(ctx, protoResponse);
                
            } catch (InvalidProtocolBufferException e) {
                System.err.println("解析 Protobuf 消息失败: " + e.getMessage());
            }
        }
        // 处理 Pong 消息
        else if (frame instanceof PongWebSocketFrame) {
            System.out.println("WebSocket Client received pong");
        } 
        // 处理关闭消息
        else if (frame instanceof CloseWebSocketFrame) {
            System.out.println("WebSocket Client received closing");
            ch.close();
        }
    }

    /**
     * 处理 Protobuf 消息
     */
    private void handleProtoMessage(ChannelHandlerContext ctx, ImProtoResponse protoResponse) {
        try {
            MsgType msgType = protoResponse.getType();
            
            switch (msgType) {
                case C2C_MSG_PUSH:
                    // 处理服务端推送的单聊消息
                    handlePushMsg(ctx, protoResponse);
                    break;
                    
                case PUSH_BATCH_MSG_IDS:
                    // 处理批量消息ID
                    handleBatchMsgIds(protoResponse);
                    break;
                    
                case C2C_ACK:
                    // 处理ACK消息（服务端推送的ACK）
                    try {
                        C2CAckReq ack = C2CAckReq.parseFrom(protoResponse.getPayload());
                        int status = ack.getStatus();
                        String statusText;
                        if (status == 1) {
                            statusText = "服务器已接收";
                        } else if (status == 3) {
                            statusText = "对方未读";
                        } else if (status == 4) {
                            statusText = "对方已读";
                        } else {
                            statusText = "未知状态(" + status + ")";
                        }
                        System.out.println("★★★ [收到ACK] msgId=" + ack.getMsgId() + ", status=" + statusText + " ★★★");
                    } catch (Exception e) {
                        System.err.println("解析 ACK 失败: " + e.getMessage());
                    }
                    break;
                    
                case C2C_WITHDRAW:
                    // 处理撤回通知（解析 C2CWithdrawReq 作为回执体）
                    try {
                        C2CWithdrawReq withdraw = C2CWithdrawReq.parseFrom(protoResponse.getPayload());
                        System.out.println("[WITHDRAW] 收到撤回通知, msgId=" + withdraw.getMsgId() + ", from=" + withdraw.getFrom() + ", to=" + withdraw.getTo() + ", chatId=" + withdraw.getChatId());
                    } catch (Exception e) {
                        System.err.println("解析 WITHDRAW 失败: " + e.getMessage());
                    }
                    break;

                
                    
                default:
                    System.out.println("未知消息类型: " + msgType);
                    break;
            }
        } catch (Exception e) {
            System.err.println("处理 Protobuf 消息异常: " + e.getMessage());
        }
    }

    /**
     * 处理推送消息（单聊消息）
     */
    private void handlePushMsg(ChannelHandlerContext ctx, ImProtoResponse protoResponse) {
        try {
            C2CMsgPush pushMsg = C2CMsgPush.parseFrom(protoResponse.getPayload());
            
            System.out.println("============================================");
            System.out.println("【收到单聊消息】");
            System.out.println("  消息ID: " + pushMsg.getMsgId());
            System.out.println("  发送人: " + pushMsg.getFrom());
            System.out.println("  接收人: " + pushMsg.getTo());
            System.out.println("  消息格式: " + pushMsg.getFormat());
            System.out.println("  消息内容: " + pushMsg.getContent());
            System.out.println("  时间戳: " + pushMsg.getTime());
            System.out.println("  会话ID: " + pushMsg.getChatId());
            System.out.println("============================================");
            
            // User1 不自动发送 ACK，需要手动测试
            // User2 会自动发送 ACK
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("解析 C2CMsgPush 失败: " + e.getMessage());
        }
    }

    /**
     * 发送 ACK 确认（提供给外部调用）
     */
    public static void sendAck(Channel channel, C2CMsgPush pushMsg, int status) {
        try {
            // 构建 ACK 请求
            C2CAckReq ackReq = C2CAckReq.newBuilder()
                    .setMsgId(pushMsg.getMsgId())
                    .setFrom(pushMsg.getTo())  // 注意：发送方和接收方对调
                    .setTo(pushMsg.getFrom())
                    .setStatus(status)  // 3:未读, 4:已读
                    .setChatId(pushMsg.getChatId())
                    .build();

            // 包装为 ImProtoRequest
            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
                    .setType(MsgType.C2C_ACK)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(ackReq.toByteArray()))
                    .build();

            // 发送
            byte[] bytes = protoRequest.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            channel.writeAndFlush(new BinaryWebSocketFrame(buf));
            System.out.println("✓ 发送 ACK 完成 - status: " + (status == 3 ? "未读" : "已读") + ", msgId: " + pushMsg.getMsgId());
            
        } catch (Exception e) {
            System.err.println("发送 ACK 失败: " + e.getMessage());
        }
    }

    /**
     * 处理批量消息ID
     */
    private void handleBatchMsgIds(ImProtoResponse protoResponse) {
        try {
            BatchMsgIdsPush resp = BatchMsgIdsPush.parseFrom(protoResponse.getPayload());
            List<String> msgIdList = resp.getMsgIdsList();
            
            System.out.println("获取到一批消息ID，数量: " + msgIdList.size());
            
            if (!CollectionUtils.isEmpty(msgIdList)) {
                msgIds.addAll(msgIdList);
                ProtobufWebsocketClient1.getMsgFlag = false;
                System.out.println("消息ID已添加到本地缓存，当前缓存数量: " + msgIds.size());
            }
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("解析 BatchMsgIdsPush 失败: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("客户端异常: " + cause.getMessage());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("客户端连接断开");
        super.channelInactive(ctx);
    }
}


