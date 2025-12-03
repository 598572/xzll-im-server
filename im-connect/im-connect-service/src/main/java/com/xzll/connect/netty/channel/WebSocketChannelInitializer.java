package com.xzll.connect.netty.channel;

import cn.hutool.extra.spring.SpringUtil;
import com.xzll.connect.config.IMConnectServerConfig;
import com.xzll.connect.netty.handler.AuthHandler;
import com.xzll.connect.netty.handler.ConnectionLimitHandler;
import com.xzll.connect.netty.handler.FlowControlHandler;
import com.xzll.connect.netty.handler.MetricsHandler;
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
        // WebSocket数据压缩（可通过Nacos配置开关）
        // 高QPS场景建议关闭（im.netty.enableCompression=false），压缩消耗CPU
        if (imConnectServerConfig.isEnableCompression()) {
            pipeline.addLast(new WebSocketServerCompressionHandler());
            log.debug("WebSocket压缩已启用");
        }
        //设置心跳 - 从配置读取IdleStateHandler检测周期
        // 注意：idleStateCheckInterval < heartBeatTime，提供容错余量
        int idleCheckInterval = imConnectServerConfig.getIdleStateCheckInterval();
        pipeline.addLast("heart-notice", new IdleStateHandler(idleCheckInterval, 0, 0, TimeUnit.SECONDS));
        log.debug("IdleStateHandler检测周期已设置为: {}秒", idleCheckInterval);

        //添加安全和统计相关handler
        // 使用Spring管理的Bean，支持@Sharable单例模式
        pipeline.addLast("connection-limit", SpringUtil.getBean(ConnectionLimitHandler.class));
        pipeline.addLast("flow-control", SpringUtil.getBean(FlowControlHandler.class));
        pipeline.addLast("metrics", new MetricsHandler()); // 无状态，可以new
        pipeline.addLast("auth", SpringUtil.getBean(AuthHandler.class)); // Spring管理的单例
        pipeline.addLast("websocket", SpringUtil.getBean(WebSocketServerHandler.class)); // Spring管理的单例

    }
}
