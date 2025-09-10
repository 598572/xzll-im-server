package com.xzll.client.client222;


import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.client.client111.WebsocketClient111;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.constant.MsgFormatEnum;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.ClientGetMsgIdsAO;
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
import org.springframework.util.CollectionUtils;
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
public class WebsocketClient222 {


    private static EventLoopGroup group = new NioEventLoopGroup();
    public static final String VALUE = "1965687073912524800";
    private String ip;
    private int port;
    private String uriStr;
    private static WebsocketClientHandler222 handler;

    public WebsocketClient222(String ip, int port) {
        this.ip = ip;
        this.port = port;
        uriStr = "ws://" + ip + ":" + port + "/websocket";
    }

    public static volatile boolean getMsgFlag = false;

    public void run() throws InterruptedException, URISyntaxException {
        // 主要是为handler(自己写的类)服务，用于初始化EasyWsHandle
        URI wsUri = new URI(uriStr);

        DefaultHttpHeaders entries = new DefaultHttpHeaders();
        entries.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJ4emxsYWRnMDIiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxOTY1Njg3MDczOTEyNTI0ODAwLCJleHAiOjE3NTc1MTI4NzUsImF1dGhvcml0aWVzIjpbIkFETUlOIl0sImp0aSI6IjZhNDQzZTZlLTg3MDUtNDk1ZC05YzQxLWU4NjliNDVlZTFjYSIsImNsaWVudF9pZCI6ImNsaWVudC1hcHAifQ.rxUxrRY-s_LMjmYXMO-8GRCdXFPsW0zCCg8TpWKFi36B8q0PtWfYiWVj3uFRk2qhf-2RAi7d4uf6d5IQOH1FtE2IgaHvEDK4RjbREIORWeKZ8ujw83wCkYslWF4iYkFoxTGtYJ9HRaQSknGs9UB5d2VYmQuN4HQDTRYesoAjelD5fY_-hdSbNxnOWfzuO0F0ywhXT5oi0SRso-EjJf6jtrsvuwxwur0G7Yyl7dYDVKVijENq0j6bAohE1eRmKsA9vMZj7OFHb4DbUpOcRREIts3kAK0VfDLFPF3rCV5UguaRiSc9EbZj3L9Ud0-qgbOyG7-6roPKs6WSlt427Psn5A");
        entries.set("uid", VALUE);

        // 设置Bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class).attr(ImConstant.USER_ID_KEY,VALUE);

        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory
                .newHandshaker(wsUri, WebSocketVersion.V13, null, true, entries, 100 * 1024 * 1024);
        handler = new WebsocketClientHandler222(webSocketClientHandshaker);



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
                //正在获取中 则等待
                if (getMsgFlag){
                    continue;
                }
                //如果没有获取 并且集合也为空 则获取（一般首次或者消息id用完了）
                if (!getMsgFlag && CollectionUtils.isEmpty(WebsocketClientHandler222.msgIds)) {
                    getMsgIds(channelFuture);
                    //标识正在获取消息id
                    getMsgFlag = true;
                    continue;
                }

                String s = sc.nextLine();

                if (!StringUtils.isEmpty(s)) {
                    ImBaseRequest<C2CSendMsgAO> imBaseRequest = new ImBaseRequest<>();
                    String msgId = WebsocketClientHandler222.msgIds.remove(0);
                    Assert.isTrue(org.apache.commons.lang3.StringUtils.isNotBlank(msgId), "无msgId可用");

                    imBaseRequest.setUrl(ImSourceUrlConstant.C2C.SEND);

                    C2CSendMsgAO c2CMsgRequestDTO = new C2CSendMsgAO();
                    c2CMsgRequestDTO.setMsgId(msgId);
                    c2CMsgRequestDTO.setMsgContent(s);
                    c2CMsgRequestDTO.setChatId("999");
                    c2CMsgRequestDTO.setToUserId(WebsocketClient111.VALUE);
                    c2CMsgRequestDTO.setFromUserId(VALUE);
                    c2CMsgRequestDTO.setMsgFormat(MsgFormatEnum.TEXT_MSG.getCode());
                    c2CMsgRequestDTO.setMsgCreateTime(System.currentTimeMillis());

                    imBaseRequest.setBody(c2CMsgRequestDTO);

                    //文本消息
                    TextWebSocketFrame byteFrame = new TextWebSocketFrame(JSONUtil.toJsonStr(imBaseRequest));
                    channelFuture.channel().writeAndFlush(byteFrame);
                }
            }
        }).start();
        // 堵塞线程，保持长连接
        channelFuture.channel().closeFuture().sync();
    }


    private void getMsgIds(ChannelFuture channelFuture) {
        ImBaseRequest<ClientGetMsgIdsAO> getMsgIds = new ImBaseRequest<ClientGetMsgIdsAO>();
        getMsgIds.setUrl(ImSourceUrlConstant.C2C.GET_BATCH_MSG_ID);
        ClientGetMsgIdsAO msgIdsDTO = new ClientGetMsgIdsAO();
        msgIdsDTO.setFromUserId(VALUE);
        getMsgIds.setBody(msgIdsDTO);
        TextWebSocketFrame textFrameResult = new TextWebSocketFrame(JSONUtil.toJsonStr(getMsgIds));
        //文本消息
        channelFuture.channel().writeAndFlush(textFrameResult);
    }

}
