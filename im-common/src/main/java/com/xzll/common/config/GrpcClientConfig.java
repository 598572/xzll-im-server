package com.xzll.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC客户端配置
 */
@Data
@ConfigurationProperties(prefix = "grpc.client")
public class GrpcClientConfig {
    
    /**
     * gRPC服务器默认端口
     */
    private int defaultPort = 9091;
    
    /**
     * 连接超时时间（秒）
     */
    private int connectTimeout = 10;
    
    /**
     * 保活时间（秒）
     */
    private int keepAliveTime = 30;
    
    /**
     * 保活超时时间（秒）
     */
    private int keepAliveTimeout = 5;
    
    /**
     * 最大入站消息大小
     */
    private int maxInboundMessageSize = 1048576; // 1MB
    
    /**
     * 是否允许无调用时保活
     */
    private boolean permitKeepAliveWithoutCalls = true;
} 