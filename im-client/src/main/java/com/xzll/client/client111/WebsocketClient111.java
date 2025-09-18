package com.xzll.client.client111;


import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.client.client222.WebsocketClient222;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ImSourceUrlConstant;
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
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:32:01
 * @Description:
 */

@Slf4j
public class WebsocketClient111 {


    public static final String VALUE = "1966479049087913984";
    private static EventLoopGroup group = new NioEventLoopGroup();

    private String ip;
    private int port;
    private String uriStr;
    private static WebsocketClientHandler111 handler;

    public static volatile boolean getMsgFlag = false;

    public WebsocketClient111(String ip, int port) {
        this.ip = ip;
        this.port = port;
        uriStr = "ws://" + ip + ":" + port + "/websocket";
    }

    public void run() throws InterruptedException, URISyntaxException {
        // 主要是为handler(自己写的类)服务，用于初始化EasyWsHandle
        URI wsUri = new URI(uriStr);
        DefaultHttpHeaders entries = new DefaultHttpHeaders();
        entries.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJ3eXEwMSIsInNjb3BlIjpbImFsbCJdLCJkZXZpY2VfdHlwZSI6MSwiaWQiOjE5NjY0NzkwNDkwODc5MTM5ODQsImV4cCI6MTc1NzY4Mzc1MSwiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiNTExNGJiYmYtOThmNy00ZjI5LTgxYzktZmExZmFkOWM2ODI4IiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.oINtCWMHD17n8u-vT7z0MNEL9zPydciAZJl5xyQUHE67et6mKn1chkTtYUB2dsg_zxoNjrpqOAVd3IM1K18qC-qVgMKH04h30Ta5zwQ7mFC7-XoZCWmB7A7RqI0xEK6Le6UFntaMmdkMVXrnuSECOBu9F-NKp5qvge_bgqqP6ZoQByHktdqEzxgf0S5hwoVjKZD8Emr8hqm7wae05LGNOCha9y6GiI5Ze_3lFoRAPdGzFiQh-BHiPoF4NF9ECoa8bz-ZprY6--Wrsj7CwljMcl072yDC5hMNPNPQC58zH8F9Zle6LeTBaj4d1icicUdKJgVyRGfCHkb5r2Gd_W2dNg");
        entries.set("uid", VALUE);


        // 设置Bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class).attr(ImConstant.USER_ID_KEY, VALUE);

        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory
                .newHandshaker(wsUri, WebSocketVersion.V13, null, true, entries, 100 * 1024 * 1024);
        handler = new WebsocketClientHandler111(webSocketClientHandshaker);

        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
//                ch.attr()

//                ch.attr( AttributeKey.valueOf(USER_ID)).set(RandomUtil.randomNumbers(2));
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new LoggingHandler(LogLevel.INFO));

                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast("heart-notice", new IdleStateHandler(11, 0, 0, TimeUnit.SECONDS));
                pipeline.addLast(handler);
            }
        });


        // 连接服务端
        //登录时传过来uid
        ChannelFuture channelFuture = bootstrap.connect(ip, port).sync();

        handler.handshakeFuture().sync();

        Scanner sc = new Scanner(System.in);


        new Thread(() -> {
            int i=0;
            while (true) {
                //正在获取中 则等待
                if (getMsgFlag){
                    continue;
                }
                //如果没有获取 并且集合也为空 则获取（一般首次或者消息id用完了）
                if (!getMsgFlag && CollectionUtils.isEmpty(WebsocketClientHandler111.msgIds)) {
                    getMsgIds(channelFuture);
                    //标识正在获取消息id
                    getMsgFlag = true;
                    continue;
                }

                String s = sc.nextLine();
//                String s="你好啊 我发消息压死你，第"+i+"条消息";
                i++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (StringUtils.isNotBlank(s)) {
                    String msgId = WebsocketClientHandler111.msgIds.remove(0);
                    Assert.isTrue(StringUtils.isNotBlank(msgId), "无msgId可用");
                    ImBaseRequest<C2CSendMsgAO> imBaseRequest = new ImBaseRequest<C2CSendMsgAO>();
                    imBaseRequest.setUrl(ImSourceUrlConstant.C2C.SEND);
                    C2CSendMsgAO c2CMsgRequestDTO = new C2CSendMsgAO();
                    c2CMsgRequestDTO.setMsgId(msgId);
                    c2CMsgRequestDTO.setMsgContent(s);
                    c2CMsgRequestDTO.setChatId("999");
                    c2CMsgRequestDTO.setToUserId(WebsocketClient222.VALUE);
                    c2CMsgRequestDTO.setFromUserId(VALUE);
                    c2CMsgRequestDTO.setMsgCreateTime(System.currentTimeMillis());
                    imBaseRequest.setBody(c2CMsgRequestDTO);

                    TextWebSocketFrame textFrame = new TextWebSocketFrame(JSONUtil.toJsonStr(imBaseRequest));
                    //文本消息
                    channelFuture.channel().writeAndFlush(textFrame);
                    channelFuture.addListener((ChannelFutureListener) lis ->
                            log.info("客户端手动发消息成功={}", textFrame.text()));
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
