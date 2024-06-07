package com.xzll.connect.netty.channel;

import cn.hutool.extra.spring.SpringUtil;
import com.xzll.connect.config.IMConnectServerConfig;
import com.xzll.connect.netty.handler.AuthHandler;
import com.xzll.connect.netty.handler.WebSocketServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {

        IMConnectServerConfig imConnectServerConfig = SpringUtil.getBean(IMConnectServerConfig.class);

        ChannelPipeline pipeline = ch.pipeline();
        // 在调试期加入日志功能，从而可以打印出报文的请求和响应细节
        if (imConnectServerConfig.isDebug()) {
            pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));
        }

        //设置解码器//第一次请求使用http相关编解码 ws请求是WebSocket13FrameDecoder
        pipeline.addLast(new HttpServerCodec());
        //聚合器，使用websocket会用到
        pipeline.addLast(new HttpObjectAggregator(65536));
        //用于大数据的分区传输
        pipeline.addLast(new ChunkedWriteHandler());
        // 支持WebSocket数据压缩
        pipeline.addLast(new WebSocketServerCompressionHandler());
        //设置心跳
        pipeline.addLast("heart-notice", new IdleStateHandler(11, 0, 0, TimeUnit.SECONDS));

        //添加自定义handler
        pipeline.addLast(new AuthHandler());
        pipeline.addLast(new WebSocketServerHandler());

    }
}
