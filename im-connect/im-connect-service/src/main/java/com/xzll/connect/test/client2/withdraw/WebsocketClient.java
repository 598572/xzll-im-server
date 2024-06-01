package com.xzll.connect.test.client2.withdraw;


import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.connect.pojo.dto.WithdrawMsgRequestDTO;
import com.xzll.connect.pojo.enums.MsgTypeEnum;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:32:01
 * @Description:
 */

@Slf4j
public class WebsocketClient {


    private static EventLoopGroup group = new NioEventLoopGroup();

    private String ip;
    private int port;
    private String uriStr;
    private static WebsocketClientHandler handler;

    public WebsocketClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        uriStr = "ws://" + ip + ":" + port + "/websocket/?userId=1002";
    }

    public void run() throws InterruptedException, URISyntaxException {
        // 主要是为handler(自己写的类)服务，用于初始化EasyWsHandle
        URI wsUri = new URI(uriStr);
        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory
                .newHandshaker(wsUri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), 100 * 1024 * 1024);
        handler = new WebsocketClientHandler(webSocketClientHandshaker);

        // 设置Bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class);

        bootstrap.handler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new LoggingHandler(LogLevel.INFO));

                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(handler);
            }
        });

        // 连接服务端
        ChannelFuture channelFuture = bootstrap.connect(ip, port).sync();
        handler.handshakeFuture().sync();

        Scanner sc = new Scanner(System.in);


        new Thread(() -> {
            while (true) {
                String s = sc.nextLine();
                //test 1 : 发送json字符串方式
//                if (!StringUtils.isEmpty(s)) {
//                    MsgBaseRequest msgBaseRequest = JSON.parseObject(s, MsgBaseRequest.class);
//                    //文本消息
//                    TextWebSocketFrame byteFrame = new TextWebSocketFrame(JsonUtil.toJson(msgBaseRequest));
//                    channelFuture.channel().writeAndFlush(byteFrame);
////                    channelFuture.addListener((ChannelFutureListener) lis ->
////                            log.info("客户端手动发消息成功={}", byteFrame.text()));
//                }

                //test 2 : 直接输入内容方式
                if (!StringUtils.isEmpty(s)) {

                    MsgBaseRequest<WithdrawMsgRequestDTO> msgBaseRequest = new MsgBaseRequest<>();

                    MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
                    msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.COMMAND_MSG.getCode());
                    msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.WITHDRAW.getCode());
                    msgBaseRequest.setMsgType(msgType);

                    WithdrawMsgRequestDTO withdrawMsgRequestDTO = new WithdrawMsgRequestDTO();
                    withdrawMsgRequestDTO.setMsgId(s);
                    withdrawMsgRequestDTO.setSessionId("网约车业务线_会话001");
                    withdrawMsgRequestDTO.setToUserId("1003");
                    withdrawMsgRequestDTO.setFromUserId("1002");


                    msgBaseRequest.setBody(withdrawMsgRequestDTO);

                    //文本消息
                    TextWebSocketFrame byteFrame = new TextWebSocketFrame(JSONUtil.toJsonStr(msgBaseRequest));
                    channelFuture.channel().writeAndFlush(byteFrame);
                }
            }
        }).start();
        // 堵塞线程，保持长连接
        channelFuture.channel().closeFuture().sync();
    }

}
