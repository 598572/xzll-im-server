//package com.xzll.client.protobuf.clien1;
//
//import cn.hutool.core.lang.Assert;
//import com.google.protobuf.ByteString;
//import com.xzll.common.constant.ImConstant;
//import com.xzll.grpc.*;
//import io.netty.bootstrap.Bootstrap;
//import io.netty.buffer.ByteBuf;
//import io.netty.buffer.Unpooled;
//import io.netty.channel.*;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.nio.NioSocketChannel;
//import io.netty.handler.codec.http.DefaultHttpHeaders;
//import io.netty.handler.codec.http.HttpClientCodec;
//import io.netty.handler.codec.http.HttpObjectAggregator;
//import io.netty.handler.codec.http.websocketx.*;
//import io.netty.handler.logging.LogLevel;
//import io.netty.handler.logging.LoggingHandler;
//import io.netty.handler.timeout.IdleStateHandler;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.util.CollectionUtils;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.util.Scanner;
//import java.util.concurrent.TimeUnit;
//
///**
// * @Author: hzz
// * @Date: 2025/10/27
// * @Description: Protobuf 协议 WebSocket 客户端
// */
//public class ProtobufWebsocketClient1 {
//
//    // 测试用户ID
//    public static final String VALUE = "1966479049087913984";
//    private static EventLoopGroup group = new NioEventLoopGroup();
//
//    private String ip;
//    private int port;
//    private String uriStr;
//    private static ProtobufWebsocketClientHandler1 handler;
//
//    public static volatile boolean getMsgFlag = false;
//
//    public ProtobufWebsocketClient1(String ip, int port) {
//        this.ip = ip;
//        this.port = port;
//        // 添加 userId 参数以满足 Nginx 的一致性哈希要求
//        uriStr = "ws://" + ip + ":" + port + "/websocket?userId=" + VALUE;
//    }
//
//    public void run() throws InterruptedException, URISyntaxException {
//        // 主要是为handler(自己写的类)服务，用于初始化EasyWsHandle
//        URI wsUri = new URI(uriStr);
//        DefaultHttpHeaders entries = new DefaultHttpHeaders();
//        entries.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJ3eXEwMSIsInNjb3BlIjpbImFsbCJdLCJkZXZpY2VfdHlwZSI6MSwiaWQiOjE5NjY0NzkwNDkwODc5MTM5ODQsImV4cCI6MTc1NzY4Mzc1MSwiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiNTExNGJiYmYtOThmNy00ZjI5LTgxYzktZmExZmFkOWM2ODI4IiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.oINtCWMHD17n8u-vT7z0MNEL9zPydciAZJl5xyQUHE67et6mKn1chkTtYUB2dsg_zxoNjrpqOAVd3IM1K18qC-qVgMKH04h30Ta5zwQ7mFC7-XoZCWmB7A7RqI0xEK6Le6UFntaMmdkMVXrnuSECOBu9F-NKp5qvge_bgqqP6ZoQByHktdqEzxgf0S5hwoVjKZD8Emr8hqm7wae05LGNOCha9y6GiI5Ze_3lFoRAPdGzFiQh-BHiPoF4NF9ECoa8bz-ZprY6--Wrsj7CwljMcl072yDC5hMNPNPQC58zH8F9Zle6LeTBaj4d1icicUdKJgVyRGfCHkb5r2Gd_W2dNg");
//        entries.set("uid", VALUE);
//
//        // 设置Bootstrap
//        Bootstrap bootstrap = new Bootstrap();
//        bootstrap.group(group);
//        bootstrap.channel(NioSocketChannel.class).attr(ImConstant.USER_ID_KEY, VALUE);
//
//        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory
//                .newHandshaker(wsUri, WebSocketVersion.V13, null, true, entries, 100 * 1024 * 1024);
//        handler = new ProtobufWebsocketClientHandler1(webSocketClientHandshaker);
//
//        bootstrap.handler(new ChannelInitializer<Channel>() {
//            @Override
//            protected void initChannel(Channel ch) {
//                ChannelPipeline pipeline = ch.pipeline();
//                pipeline.addLast(new LoggingHandler(LogLevel.INFO));
//                pipeline.addLast(new HttpClientCodec());
//                pipeline.addLast(new HttpObjectAggregator(65536));
//                pipeline.addLast("heart-notice", new IdleStateHandler(11, 0, 0, TimeUnit.SECONDS));
//                pipeline.addLast(handler);
//            }
//        });
//
//        // 连接服务端
//        ChannelFuture channelFuture = bootstrap.connect(ip, port).sync();
//        handler.handshakeFuture().sync();
//
//        Scanner sc = new Scanner(System.in);
//
//        new Thread(() -> {
//            int i = 0;
//            while (true) {
//                // 正在获取中则等待
//                if (getMsgFlag) {
//                    continue;
//                }
//                // 如果没有获取并且集合也为空则获取（一般首次或者消息id用完了）
//                if (!getMsgFlag && CollectionUtils.isEmpty(ProtobufWebsocketClientHandler1.msgIds)) {
//                    getMsgIds(channelFuture);
//                    // 标识正在获取消息id
//                    getMsgFlag = true;
//                    continue;
//                }
//
//                String s = sc.nextLine();
//                i++;
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                if (StringUtils.isNotBlank(s)) {
//                    // 支持输入: withdraw <msgId> 触发撤回
//                    if (s.startsWith("withdraw ")) {
//                        String msgIdWithdraw = s.substring("withdraw ".length()).trim();
//                        sendWithdraw(channelFuture, msgIdWithdraw, VALUE, "1966369607918948352");
//                        continue;
//                    }
//                    String msgId = ProtobufWebsocketClientHandler1.msgIds.remove(0);
//                    Assert.isTrue(StringUtils.isNotBlank(msgId), "无msgId可用");
//
//                    // 构建 Protobuf C2C 发送消息请求
//                    C2CSendReq c2cSendReq = C2CSendReq.newBuilder()
//                            .setMsgId(msgId)
//                            .setFrom(VALUE)
//                            .setTo("1966369607918948352")  // 接收人ID
//                            .setFormat(1)  // 1:文本消息
//                            .setContent(s)
//                            .setTime(System.currentTimeMillis())
//                            .setChatId("100-1-1966369607918948352-1966479049087913984")
//                            .build();
//
//                    // 包装为 ImProtoRequest
//                    ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
//                            .setType(MsgType.C2C_SEND)
//                            .setPayload(ByteString.copyFrom(c2cSendReq.toByteArray()))
//                            .build();
//
//                    // 发送 Protobuf 二进制消息
//                    byte[] bytes = protoRequest.toByteArray();
//                    ByteBuf buf = Unpooled.wrappedBuffer(bytes);
//                    BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(buf);
//                    channelFuture.channel().writeAndFlush(binaryFrame);
//                    channelFuture.addListener((ChannelFutureListener) lis ->
//                            System.out.println("客户端手动发送Protobuf消息成功，msgId=" + msgId + ", content=" + s));
//                }
//            }
//        }).start();
//
//        // 堵塞线程，保持长连接
//        channelFuture.channel().closeFuture().sync();
//    }
//
//    private void sendWithdraw(ChannelFuture channelFuture, String msgId, String from, String to) {
//        try {
//            com.xzll.grpc.C2CWithdrawReq withdrawReq = com.xzll.grpc.C2CWithdrawReq.newBuilder()
//                    .setMsgId(msgId)
//                    .setFrom(from)
//                    .setTo(to)
//                    .setChatId("100-1-" + from + "-" + to)
//                    .build();
//
//            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
//                    .setType(MsgType.C2C_WITHDRAW)
//                    .setPayload(ByteString.copyFrom(withdrawReq.toByteArray()))
//                    .build();
//
//            byte[] bytes = protoRequest.toByteArray();
//            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
//            BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(buf);
//            channelFuture.channel().writeAndFlush(binaryFrame);
//            System.out.println("[user1] 发送撤回请求完成，msgId=" + msgId);
//        } catch (Exception e) {
//            System.err.println("[user1] 发送撤回失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 获取消息ID列表
//     */
//    private void getMsgIds(ChannelFuture channelFuture) {
//        try {
//            // 构建获取消息ID请求
//            GetBatchMsgIdsReq getBatchMsgIdsReq = GetBatchMsgIdsReq.newBuilder()
//                    .setUserId(VALUE)
//                    .build();
//
//            // 包装为 ImProtoRequest
//            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
//                    .setType(MsgType.GET_BATCH_MSG_IDS)
//                    .setPayload(ByteString.copyFrom(getBatchMsgIdsReq.toByteArray()))
//                    .build();
//
//            // 发送 Protobuf 二进制消息
//            byte[] bytes = protoRequest.toByteArray();
//            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
//            BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(buf);
//            channelFuture.channel().writeAndFlush(binaryFrame);
//            System.out.println("发送获取消息ID请求");
//        } catch (Exception e) {
//            System.err.println("获取消息ID失败: " + e.getMessage());
//        }
//    }
//
//}
//
//
