package com.xzll.connect.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;


@Setter
@Getter
@Component
@RefreshScope
@ConfigurationProperties(prefix = "im.netty")
public class IMConnectServerConfig {

    /**
     * netty端口
     */
    private int port;
    /**
     * accept 队列的大小
     */
    private int soBackLog;
    /**
     * 在调试期加入日志功能，从而可以打印出报文的请求和响应细节
     */
    private boolean debug;
    /**
     * 心跳时间
     */
    private long heartBeatTime;

}
