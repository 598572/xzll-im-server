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
}


