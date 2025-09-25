package com.xzll.common.grpc;

import com.xzll.common.utils.RedissonUtils;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.common.config.GrpcClientConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 智能gRPC客户端管理器 - 连接复用、自动重连、负载均衡
 */
@Slf4j
public class SmartGrpcClientManager {
    
    @Resource
    private RedissonUtils redissonUtils;
    @Resource
    private GrpcClientConfig grpcClientConfig;
    
    // 连接池：key = ip:port, value = 连接信息
    private final Map<String, ChannelInfo> channelPool = new ConcurrentHashMap<>();
    
    // 连接监控线程池
    private final ScheduledExecutorService healthCheckExecutor = 
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "grpc-health-check");
            t.setDaemon(true);
            return t;
        });
    
    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successRequests = new AtomicLong(0);
    private final AtomicLong failureRequests = new AtomicLong(0);
    
    @PostConstruct
    public void init() {
        // 启动健康检查
        startHealthCheck();
        log.info("SmartGrpcClientManager 初始化完成, 默认端口:{}", grpcClientConfig.getDefaultPort());
    }
    
    @PreDestroy
    public void shutdown() {
        healthCheckExecutor.shutdown();
        closeAllChannels();
        log.info("SmartGrpcClientManager 已关闭");
    }
    
    /**
     * 智能获取stub - 自动路由、连接复用
     */
    public GrpcStubWrapper getStub(String userId) {
        // 1. 获取用户所在服务器
        String routeAddress = redissonUtils.getHash(
            ImConstant.RedisKeyConstant.ROUTE_PREFIX, 
            userId
        );
        
        if (routeAddress == null) {
            throw new RuntimeException("用户 " + userId + " 不在线或未找到路由信息");
        }
        
        String ip = NettyAttrUtil.getIpStr(routeAddress);
        int port = grpcClientConfig.getDefaultPort(); // 使用配置端口
        log.debug("为用户{} 路由到服务器: {}:{}", userId, ip, port);
        
        return getStubByIP(ip, port);
    }
    
    /**
     * 根据IP获取stub
     */
    public GrpcStubWrapper getStubByIP(String ip, int port) {
        String key = ip + ":" + port;
        
        ChannelInfo channelInfo = channelPool.compute(key, (k, existing) -> {
            if (existing != null && existing.isHealthy()) {
                existing.incrementUsage();
                return existing;
            }
            
            // 创建新连接
            ChannelInfo newInfo = createChannel(ip, port);
            log.info("创建新的gRPC连接到: {}:{}", ip, port);
            return newInfo;
        });
        
        return new GrpcStubWrapper(channelInfo, this);
    }
    
    /**
     * 批量获取stub - 按服务器分组
     */
    public Map<String, GrpcStubWrapper> getStubsForUsers(List<String> userIds) {
        Map<String, List<String>> serverUserMap = new ConcurrentHashMap<>();
        int port = grpcClientConfig.getDefaultPort();
        
        // 按服务器分组用户
        userIds.forEach(userId -> {
            try {
                String routeAddress = redissonUtils.getHash(
                    ImConstant.RedisKeyConstant.ROUTE_PREFIX, 
                    userId
                );
                
                if (routeAddress != null) {
                    String ip = NettyAttrUtil.getIpStr(routeAddress);
                    String key = ip + ":" + port;
                    serverUserMap.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(userId);
                }
            } catch (Exception e) {
                log.warn("获取用户 {} 路由信息失败: {}", userId, e.getMessage());
            }
        });
        
        // 为每个服务器创建一个stub
        Map<String, GrpcStubWrapper> result = new ConcurrentHashMap<>();
        serverUserMap.forEach((serverKey, users) -> {
            String[] parts = serverKey.split(":");
            String ip = parts[0];
            int p = Integer.parseInt(parts[1]);
            
            GrpcStubWrapper stub = getStubByIP(ip, p);
            result.put(serverKey, stub);
        });
        
        return result;
    }
    
    /**
     * 创建新连接
     */
    private ChannelInfo createChannel(String ip, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ip, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .maxInboundMessageSize(10 * 1024 * 1024) // 10MB
                .maxInboundMetadataSize(8192) // 8KB
                .build();
        
        return new ChannelInfo(ip, port, channel);
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                checkChannelHealth();
            } catch (Exception e) {
                log.error("健康检查异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 检查连接健康状态
     */
    private void checkChannelHealth() {
        channelPool.forEach((key, channelInfo) -> {
            if (channelInfo.isExpired() || !channelInfo.isHealthy()) {
                log.info("关闭不健康的连接: {}", key);
                closeChannel(key);
            }
        });
    }
    
    /**
     * 关闭指定连接
     */
    public void closeChannel(String key) {
        ChannelInfo channelInfo = channelPool.remove(key);
        if (channelInfo != null) {
            channelInfo.close();
            log.info("关闭gRPC连接: {}", key);
        }
    }
    
    /**
     * 关闭所有连接
     */
    public void closeAllChannels() {
        channelPool.forEach((key, channelInfo) -> {
            try {
                channelInfo.close();
                log.info("关闭gRPC连接: {}", key);
            } catch (Exception e) {
                log.error("关闭gRPC连接异常: {}", key, e);
            }
        });
        channelPool.clear();
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalChannels", channelPool.size());
        stats.put("totalRequests", totalRequests.get());
        stats.put("successRequests", successRequests.get());
        stats.put("failureRequests", failureRequests.get());
        stats.put("successRate", 
            totalRequests.get() > 0 ? 
            (double) successRequests.get() / totalRequests.get() : 0.0);
        
        return stats;
    }
    
    /**
     * 记录请求统计
     */
    public void recordRequest(boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successRequests.incrementAndGet();
        } else {
            failureRequests.incrementAndGet();
        }
    }
    
    /**
     * 连接信息包装类
     */
    public static class ChannelInfo {
        private final String ip;
        private final int port;
        private final ManagedChannel channel;
        private final AtomicInteger usageCount;
        private final long createTime;
        private volatile boolean healthy;
        
        public ChannelInfo(String ip, int port, ManagedChannel channel) {
            this.ip = ip;
            this.port = port;
            this.channel = channel;
            this.usageCount = new AtomicInteger(1);
            this.createTime = System.currentTimeMillis();
            this.healthy = true;
        }
        
        public void incrementUsage() {
            usageCount.incrementAndGet();
        }
        
        public boolean isHealthy() {
            return healthy && !channel.isShutdown() && !channel.isTerminated();
        }
        
        public boolean isExpired() {
            // 连接超过5分钟且使用次数为0，则过期
            return System.currentTimeMillis() - createTime > 300000 && usageCount.get() == 0;
        }
        
        public void close() {
            healthy = false;
            if (!channel.isShutdown()) {
                channel.shutdown();
                try {
                    if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                        channel.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    channel.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        public ManagedChannel getChannel() { return channel; }
        public String getIp() { return ip; }
        public int getPort() { return port; }
        public int getUsageCount() { return usageCount.get(); }
    }
    
    /**
     * gRPC Stub包装类
     */
    public static class GrpcStubWrapper {
        private final ChannelInfo channelInfo;
        private final SmartGrpcClientManager manager;
        
        public GrpcStubWrapper(ChannelInfo channelInfo, SmartGrpcClientManager manager) {
            this.channelInfo = channelInfo;
            this.manager = manager;
        }
        
        public ChannelInfo getChannelInfo() {
            return channelInfo;
        }
        
        public void close() {
            manager.closeChannel(channelInfo.getIp() + ":" + channelInfo.getPort());
        }
    }
} 