package com.xzll.connect.netty.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

/**
 * @Author: hzz
 * @Date: 2024/7/3 22:49:07
 * @Description: 业务指标记录
 */
@ChannelHandler.Sharable
public class MetricsHandler extends ChannelInboundHandlerAdapter {
    private static final Counter requests = Counter.build()
            .name("requests_total")
            .help("Total requests.")
            .register();
    private static final Histogram requestLatency = Histogram.build()
            .name("requests_latency_seconds")
            .help("Request latency in seconds.")
            .register();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //更新请求计数器
        requests.inc();
        //开始计时
        Histogram.Timer requestTimer = requestLatency.startTimer();
        try {
            super.channelRead(ctx, msg);
        } finally {
            // 记录处理时间
            requestTimer.observeDuration();
        }
    }
}
