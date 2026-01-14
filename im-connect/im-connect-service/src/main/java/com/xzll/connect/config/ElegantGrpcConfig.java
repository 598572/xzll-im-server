package com.xzll.connect.config;

import com.xzll.connect.grpc.MessageServiceGrpcImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 优雅的gRPC配置类
 * 
 * 注意：Spring Boot 3.x 中，移除了 @Profile("!test") 和 @ConfigurationProperties
 * 以确保 gRPC 服务器在所有环境中都能正常启动
 */
@Slf4j
@Configuration
@Data
public class ElegantGrpcConfig {
    
    /**
     * 注入 Spring 管理的 MessageServiceGrpcImpl
     */
    @Resource
    private MessageServiceGrpcImpl messageServiceGrpcImpl;
    
    /**
     * gRPC服务器端口
     */
    @Value("${grpc.server.port:9091}")
    private int port;
    
    /**
     * 最大入站消息大小
     */
    @Value("${grpc.server.max-inbound-message-size:1048576}")
    private int maxInboundMessageSize;
    
    /**
     * 最大入站元数据大小
     */
    @Value("${grpc.server.max-inbound-metadata-size:8192}")
    private int maxInboundMetadataSize;
    
    /**
     * 保活时间（秒）
     */
    @Value("${grpc.server.keep-alive-time:30}")
    private int keepAliveTime;
    
    /**
     * 保活超时时间（秒）
     */
    @Value("${grpc.server.keep-alive-timeout:5}")
    private int keepAliveTimeout;
    
    /**
     * 是否允许无调用时保活
     */
    @Value("${grpc.server.permit-keep-alive-without-calls:true}")
    private boolean permitKeepAliveWithoutCalls;
    
    private Server grpcServer;
    
    @PostConstruct
    public void startGrpcServer() {
        log.info("======== gRPC服务器启动开始 ========");
        log.info("gRPC配置: port={}, maxInboundMessageSize={}, keepAliveTime={}s", 
            port, maxInboundMessageSize, keepAliveTime);
        
        // 检查依赖注入
        if (messageServiceGrpcImpl == null) {
            log.error("gRPC服务启动失败: MessageServiceGrpcImpl 未注入（为null）");
            throw new IllegalStateException("MessageServiceGrpcImpl is null, gRPC server cannot start");
        }
        log.info("MessageServiceGrpcImpl 注入成功: {}", messageServiceGrpcImpl.getClass().getName());
        
        try {
            grpcServer = ServerBuilder.forPort(port)
                    .addService(messageServiceGrpcImpl)
                    .maxInboundMessageSize(maxInboundMessageSize)
                    .maxInboundMetadataSize(maxInboundMetadataSize)
                    .keepAliveTime(keepAliveTime, java.util.concurrent.TimeUnit.SECONDS)
                    .keepAliveTimeout(keepAliveTimeout, java.util.concurrent.TimeUnit.SECONDS)
                    .permitKeepAliveWithoutCalls(permitKeepAliveWithoutCalls)
                    .build()
                    .start();
            
            log.info("======== gRPC服务器启动成功 监听端口: {} ========", port);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("正在关闭gRPC服务器...");
                stopGrpcServer();
                log.info("gRPC服务器已关闭");
            }));
        } catch (IOException e) {
            log.error("gRPC服务器启动失败: port={}, error={}", port, e.getMessage(), e);
            throw new RuntimeException("gRPC服务器启动失败", e);
        }
    }
    
    @PreDestroy
    public void stopGrpcServer() {
        if (grpcServer != null) {
            grpcServer.shutdown();
            try {
                if (!grpcServer.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    grpcServer.shutdownNow();
                    if (!grpcServer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        log.error("gRPC服务器无法正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                grpcServer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Bean
    public Server grpcServer() {
        return grpcServer;
    }
} 