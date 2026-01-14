package com.xzll.business.config;

import com.xzll.common.config.GrpcClientConfig;
import com.xzll.common.grpc.ElegantGrpcMessageServiceImpl;
import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.grpc.SmartGrpcClientManager;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: im-business-service的gRPC客户端配置
 * 
 * 注意：不能使用 new 创建对象，否则 @Resource 注入会失效！
 * 必须通过构造函数传递依赖，确保 Spring 依赖注入正常工作。
 */
@Slf4j
@Configuration
public class GrpcClientConfiguration {

    @Resource
    private RedissonUtils redissonUtils;

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
     * 注意：通过构造函数注入依赖，避免 @Resource 在 new 对象时失效
     */
    @Bean
    public SmartGrpcClientManager smartGrpcClientManager(GrpcClientConfig grpcClientConfig) {
        log.info("初始化智能gRPC客户端管理器");
        return new SmartGrpcClientManager(redissonUtils, grpcClientConfig);
    }

    /**
     * gRPC消息服务 - 仅客户端功能
     * 注意：通过构造函数注入依赖，避免 @Resource 在 new 对象时失效
     */
    @Bean
    public GrpcMessageService grpcMessageService(SmartGrpcClientManager smartGrpcClientManager, GrpcClientConfig grpcClientConfig) {
        log.info("初始化gRPC消息服务（仅客户端）");
        return new ElegantGrpcMessageServiceImpl(smartGrpcClientManager, grpcClientConfig);
    }
}
