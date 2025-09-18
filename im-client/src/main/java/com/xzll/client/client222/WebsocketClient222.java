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
    public static final String VALUE = "1966369607918948352";
    private String ip;
    private int port;
    private String uriStr;
    private static WebsocketClientHandler222 handler;

    public WebsocketClient222(String ip, int port) {
        this.ip = ip;
        this.port = port;
        uriStr = "ws://" + ip + ":" + port + "/websocket?userId="+VALUE;
    }

    public static volatile boolean getMsgFlag = false;

    public void run() throws InterruptedException, URISyntaxException {
        // 主要是为handler(自己写的类)服务，用于初始化EasyWsHandle
        URI wsUri = new URI(uriStr);

        DefaultHttpHeaders entries = new DefaultHttpHeaders();
        entries.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJoaGhiYmIiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxOTY2MzY5NjA3OTE4OTQ4MzUyLCJleHAiOjE3NTgxODAwMTQsImF1dGhvcml0aWVzIjpbIkFETUlOIl0sImp0aSI6IjRlYmJkY2Y4LWFhMDUtNDQ1Mi04Mjk4LTdlMTJhOTVhZDQwMyIsImNsaWVudF9pZCI6ImNsaWVudC1hcHAifQ.K5SrLh91xdLvVKLzq-wsYIkW1tSkZkWJaPCFnYtkigml6yW6MzKQsB3m_4vjmvnpLoXxelfCPaI9ajBdskyC7vghjcCU59DAxH0wjY-M7Rs5aQNZ5NDjdeQ_zKXs7AK3QDSiVRh8OUJ5Gd6h_pbgXlbEiv35u_ddVEbvFeiFz-WFBz6EZX5fgcSFU0kt2FKL_WhHvcCquvoF7OgSHqNBe_nKa92erUVLDVTTpNesWs_8YAW3UXRfZnRDph9zmRmxcPdfcxWek01K7hgHWxDYCrcpYFR79ZNfG7SdczJIYh4YXuwKZ_Ku5VLKp5CXtr2uY86WPSqhj99YTjGotCq5AA");
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
