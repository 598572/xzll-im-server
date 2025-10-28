package com.xzll.client.protobuf.clien2;

import cn.hutool.core.lang.Assert;
import com.google.protobuf.ByteString;
import com.xzll.common.constant.ImConstant;
import com.xzll.grpc.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class ProtobufWebsocketClient2 {

    public static final String VALUE = "1966369607918948352"; // user2
    private static EventLoopGroup group = new NioEventLoopGroup();

    private String ip;
    private int port;
    private String uriStr;
    private static ProtobufWebsocketClientHandler2 handler;

    public static volatile boolean getMsgFlag = false;

    public ProtobufWebsocketClient2(String ip, int port) {
        this.ip = ip;
        this.port = port;
        uriStr = "ws://" + ip + ":" + port + "/websocket";
    }

    public void run() throws InterruptedException, URISyntaxException {
        URI wsUri = new URI(uriStr);
        DefaultHttpHeaders entries = new DefaultHttpHeaders();
        entries.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJ3eXEwMSIsInNjb3BlIjpbImFsbCJdLCJkZXZpY2VfdHlwZSI6MSwiaWQiOjE5NjYzNjk2MDc5MTg5NDgzNTIsImV4cCI6MTc1NzY4Mzc1MSwiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiNTExNGJiYmYtOThmNy00ZjI5LTgxYzktZmExZmFkOWM2ODI4IiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.oINtCWMHD17n8u-vT7z0MNEL9zPydciAZJl5xyQUHE67et6mKn1chkTtYUB2dsg_zxoNjrpqOAVd3IM1K18qC-qVgMKH04h30Ta5zwQ7mFC7-XoZCWmB7A7RqI0xEK6Le6UFntaMmdkMVXrnuSECOBu9F-NKp5qvge_bgqqP6ZoQByHktdqEzxgf0S5hwoVjKZD8Emr8hqm7wae05LGNOCha9y6GiI5Ze_3lFoRAPdGzFiQh-BHiPoF4NF9ECoa8bz-ZprY6--Wrsj7CwljMcl072yDC5hMNPNPQC58zH8F9Zle6LeTBaj4d1icicUdKJgVyRGfCHkb5r2Gd_W2dNg");
        entries.set("uid", VALUE);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class).attr(ImConstant.USER_ID_KEY, VALUE);

        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory
                .newHandshaker(wsUri, WebSocketVersion.V13, null, true, entries, 100 * 1024 * 1024);
        handler = new ProtobufWebsocketClientHandler2(webSocketClientHandshaker);

        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast("heart-notice", new IdleStateHandler(11, 0, 0, TimeUnit.SECONDS));
                pipeline.addLast(handler);
            }
        });

        ChannelFuture channelFuture = bootstrap.connect(ip, port).sync();
        handler.handshakeFuture().sync();

        Scanner sc = new Scanner(System.in);
        new Thread(() -> {
            while (true) {
                String s = sc.nextLine();
                if (StringUtils.isNotBlank(s)) {
                    // user2 可以主动发起撤回，输入形如: withdraw <msgId>
                    if (s.startsWith("withdraw ")) {
                        String msgId = s.substring("withdraw ".length()).trim();
                        sendWithdraw(channelFuture, msgId, ProtobufWebsocketClient2.VALUE, "1966479049087913984");
                    }
                }
            }
        }).start();

        channelFuture.channel().closeFuture().sync();
    }

    private void sendWithdraw(ChannelFuture channelFuture, String msgId, String from, String to) {
        try {
            C2CWithdrawReq withdrawReq = C2CWithdrawReq.newBuilder()
                    .setMsgId(msgId)
                    .setFrom(from)
                    .setTo(to)
                    .setChatId("100-1-" + to + "-" + from)
                    .build();

            ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
                    .setType(MsgType.C2C_WITHDRAW)
                    .setPayload(ByteString.copyFrom(withdrawReq.toByteArray()))
                    .build();

            byte[] bytes = protoRequest.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(buf);
            channelFuture.channel().writeAndFlush(binaryFrame);
            System.out.println("发送撤回请求完成，msgId=" + msgId);
        } catch (Exception e) {
            System.err.println("发送撤回失败: " + e.getMessage());
        }
    }
}


