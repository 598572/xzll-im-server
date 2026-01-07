package com.xzll.connect.config;

import com.xzll.connect.grpc.MessageServiceGrpcImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import lombok.Data;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 优雅的gRPC配置类
 */
@Slf4j
@Configuration
@Profile("!test") // 测试环境不启动gRPC服务器
@ConfigurationProperties(prefix = "grpc.server")
@Data
public class ElegantGrpcConfig {
    
    /**
     * gRPC服务器端口
     */
    private int port = 9091;
    
    /**
     * 最大入站消息大小
     */
    private int maxInboundMessageSize = 1048576; // 1MB
    
    /**
     * 最大入站元数据大小
     */
    private int maxInboundMetadataSize = 8192; // 8KB
    
    /**
     * 保活时间（秒）
     */
    private int keepAliveTime = 30;
    
    /**
     * 保活超时时间（秒）
     */
    private int keepAliveTimeout = 5;
    
    /**
     * 是否允许无调用时保活
     */
    private boolean permitKeepAliveWithoutCalls = true;
    
    private Server grpcServer;
    
    @PostConstruct
    public void startGrpcServer() throws IOException {
        log.info("启动gRPC服务器，端口: {}, 最大消息大小: {} bytes", port, maxInboundMessageSize);
        
        grpcServer = ServerBuilder.forPort(port)
                .addService(new MessageServiceGrpcImpl())
                .maxInboundMessageSize(maxInboundMessageSize)
                .maxInboundMetadataSize(maxInboundMetadataSize)
                .keepAliveTime(keepAliveTime, java.util.concurrent.TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(permitKeepAliveWithoutCalls)
                .build()
                .start();
        
        log.info("gRPC服务器启动成功，监听端口: {}", port);
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭gRPC服务器...");
            stopGrpcServer();
            log.info("gRPC服务器已关闭");
        }));
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