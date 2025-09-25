package com.xzll.business.config;

import com.xzll.common.config.GrpcClientConfig;
import com.xzll.common.grpc.ElegantGrpcMessageServiceImpl;
import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.grpc.SmartGrpcClientManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: im-business-service的gRPC客户端配置
 */
@Slf4j
@Configuration
public class GrpcClientConfiguration {

    /**
     * gRPC客户端配置
     */
    @Bean
    @ConfigurationProperties(prefix = "grpc.client")
    public GrpcClientConfig grpcClientConfig() {
        log.info("初始化gRPC客户端配置");
        return new GrpcClientConfig();
    }

    /**
     * 智能gRPC客户端管理器
     */
    @Bean
    public SmartGrpcClientManager smartGrpcClientManager() {
        log.info("初始化智能gRPC客户端管理器");
        return new SmartGrpcClientManager();
    }

    /**
     * gRPC消息服务 - 仅客户端功能
     */
    @Bean
    public GrpcMessageService grpcMessageService() {
        log.info("初始化gRPC消息服务（仅客户端）");
        return new ElegantGrpcMessageServiceImpl();
    }
}
