
package com.xzll.client.json.client222;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:39:23
 * @Description:
 */
public class WebsocketClientHandler222 extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    public static List<String> msgIds = new ArrayList<>();

    public WebsocketClientHandler222(WebSocketClientHandshaker handshaker) {
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
        System.out.println("客户端连接建立,当前uid:" + ctx.channel().attr(ImConstant.USER_ID_KEY).get());
        // 在通道连接成功后发送握手连接
        handshaker.handshake(ctx.channel());
        super.channelActive(ctx);
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
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;

            System.out.println("用户：（ " + WebsocketClient222.VALUE + " ），接收到TextWebSocketFrame消息，消息内容是: " + textFrame.text());
            ImBaseResponse imBaseResponse = JSON.parseObject(textFrame.text(), ImBaseResponse.class);


            //模拟接收到单聊消息后 receive client 返回已读 or 未读

            /**
             * 给发送者回复未读/已读消息
             */
            if (Objects.equals(ImSourceUrlConstant.C2C.SEND,imBaseResponse.getUrl())) {

                JSONObject jsonObject = JSON.parseObject(textFrame.text());
                String msgId = jsonObject.getObject("msgId", String.class);
                String toUserId = jsonObject.getObject("toUserId", String.class);
                String fromUserId = jsonObject.getObject("fromUserId", String.class);

                C2CReceivedMsgAckAO c2CReceivedMsgAckAO = new C2CReceivedMsgAckAO();
                c2CReceivedMsgAckAO.setMsgId(msgId);
                c2CReceivedMsgAckAO.setFromUserId(toUserId);
                c2CReceivedMsgAckAO.setToUserId(fromUserId);
                //模拟接收方未读
                c2CReceivedMsgAckAO.setMsgStatus(MsgStatusEnum.MsgStatus.UN_READ.getCode());


                ImBaseRequest imBaseRequest = new ImBaseRequest<>();

                imBaseRequest.setBody(c2CReceivedMsgAckAO);

                //模拟已读
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(imBaseRequest)));
                System.out.println("发送未读完成，data: " + JSONUtil.toJsonStr(imBaseRequest));

                //模拟已读
                imBaseRequest.setUrl(ImSourceUrlConstant.C2C.TO_USER_READ_ACK);
                c2CReceivedMsgAckAO.setMsgStatus(MsgStatusEnum.MsgStatus.READED.getCode());
                imBaseRequest.setBody(c2CReceivedMsgAckAO);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(imBaseRequest)));
                System.out.println("发送已读完成，data: " + JSONUtil.toJsonStr(imBaseRequest));

                //模拟撤回消息
                ImBaseRequest imBaseRequestWithdraw = new ImBaseRequest<>();
                imBaseRequestWithdraw.setUrl(ImSourceUrlConstant.C2C.WITHDRAW);
                C2CWithdrawMsgAO withdrawMsgAO = new C2CWithdrawMsgAO();
                withdrawMsgAO.setFromUserId(toUserId);
                withdrawMsgAO.setToUserId(fromUserId);
                withdrawMsgAO.setMsgId(msgId);
                withdrawMsgAO.setWithdrawFlag(MsgStatusEnum.MsgWithdrawStatus.YES.getCode());
                imBaseRequestWithdraw.setBody(withdrawMsgAO);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(imBaseRequestWithdraw)));
                System.out.println("发送撤回消息完成，data: " + JSONUtil.toJsonStr(imBaseRequestWithdraw));
            }
            /**
             * 处理打印单聊消息
             */
            if (Objects.equals(ImSourceUrlConstant.C2C.SEND,imBaseResponse.getUrl())) {
                    JSONObject jsonObject = JSON.parseObject(textFrame.text());
                    String msgContent = jsonObject.getObject("msgContent", String.class);
                    String fromUserId = jsonObject.getObject("fromUserId", String.class);
                    System.out.println("【" + fromUserId + "】: " + msgContent);

            }
            /**
             * 处理获取消息id的消息
             */
            if (Objects.equals(ImSourceUrlConstant.C2C.GET_BATCH_MSG_ID,imBaseResponse.getUrl())) {
                    JSONObject jsonObject = JSON.parseObject(textFrame.text());
                    List<String> msgIds = jsonObject.getObject("msgIds", List.class);
                    System.out.println("获取到一批消息id,长度:" + msgIds.size());
                    if (!CollectionUtils.isEmpty(msgIds)) {
                        WebsocketClientHandler222.msgIds.addAll(msgIds);
                        WebsocketClient222.getMsgFlag = false;
                    }
            }
        } else if (frame instanceof BinaryWebSocketFrame) {
            System.out.println("客户端：接收到BinaryWebSocketFrame消息，消息内容是-- ");
            ByteBuf content = frame.content();
            byte[] result = new byte[content.readableBytes()];
            content.readBytes(result);
            for (byte b : result) {
                System.out.print(b);
                System.out.print(",");
            }
            System.out.println();
        } else if (frame instanceof PongWebSocketFrame) {
            System.out.println("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            System.out.println("WebSocket Client received closing");
            ch.close();
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable arg1) {
        System.out.println("异常发生");
        arg1.printStackTrace();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("客户端连接断开");
        super.channelInactive(ctx);
    }
}
