package com.xzll.connect.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Setter
@Getter
@RefreshScope
@ConfigurationProperties(prefix = "im.netty")
public class IMConnectServerConfig {

    /**
     * netty端口
     */
    private int nettyPort;

    /**
     * prometheus采集指标 端口
     */
    private int prometheusPort;
    /**
     * accept 队列的大小
     */
    private int soBackLog;
    /**
     * 在调试期加入日志功能，从而可以打印出报文的请求和响应细节
     */
    private boolean debug;
    /**
     * 心跳超时时间（秒）- 默认30秒
     * 客户端需要在这个时间内发送心跳，否则连接会被关闭
     */
    private long heartBeatTime = 30;
    
    /**
     * 最大心跳失败次数 - 默认3次
     * 连续失败达到这个次数后，关闭连接
     */
    private int maxHeartbeatFailures = 3;
    
    /**
     * 主动心跳发送间隔（秒）- 默认25秒
     * 服务端主动向客户端发送ping的间隔
     */
    private int activeHeartbeatInterval = 25;

}
