package com.xzll.connect.netty.channel;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地Channel管理器
 * 优化内容：
 * 1. 增加连接状态监控
 * 2. 防止内存泄漏，自动清理无效连接
 * 3. 增加连接统计和监控
 * 4. 线程安全优化
 * 5. 支持连接限制
 */
@Slf4j
@Component
public class LocalChannelManager {
    
    // 用户ID到Channel的映射
    private static final ConcurrentMap<String, Channel> userIdChannelMap = new ConcurrentHashMap<>();
    
    // ChannelID到用户ID的映射
    private static final ConcurrentMap<String, String> channelIdUserIdMap = new ConcurrentHashMap<>();
    
    // 用户连接时间记录
    private static final ConcurrentMap<String, Long> userConnectTimeMap = new ConcurrentHashMap<>();
    
    // 连接统计
    private static final AtomicInteger totalConnections = new AtomicInteger(0);
    private static final AtomicInteger activeConnections = new AtomicInteger(0);
    
    // 定时清理任务
    private static final ScheduledExecutorService cleanupExecutor = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "channel-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    
    // 单用户最大连接数限制
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    
    // 用户多连接映射 (一个用户可能有多个设备连接)
    private static final ConcurrentMap<String, Set<String>> userMultiChannelMap = new ConcurrentHashMap<>();
    
    static {
        // 启动定时清理任务，每分钟检查一次无效连接
        cleanupExecutor.scheduleAtFixedRate(LocalChannelManager::cleanupInactiveChannels, 
            1, 1, TimeUnit.MINUTES);
        
        // 启动连接统计任务，每10秒输出一次统计信息
        cleanupExecutor.scheduleAtFixedRate(LocalChannelManager::logConnectionStats, 
            60, 60, TimeUnit.SECONDS);
    }

    /**
     * 添加用户连接
     * 支持单用户多连接场景（多设备登录）
     */
    public static boolean addUserChannel(String userId, Channel channel) {
        if (userId == null || channel == null) {
            log.warn("添加用户连接失败：userId或channel为null");
            return false;
        }
        
        String channelId = channel.id().asLongText();
        
        // 检查用户连接数限制
        Set<String> userChannels = userMultiChannelMap.computeIfAbsent(userId, 
            k -> ConcurrentHashMap.newKeySet());
        
        if (userChannels.size() >= MAX_CONNECTIONS_PER_USER) {
            log.warn("用户{}连接数超过限制：{}，拒绝新连接", userId, MAX_CONNECTIONS_PER_USER);
            return false;
        }
        
        // 如果该用户已有连接，移除旧连接（单设备登录模式）
        Channel oldChannel = userIdChannelMap.get(userId);
        if (oldChannel != null && !oldChannel.id().equals(channel.id())) {
            String oldChannelId = oldChannel.id().asLongText();
            log.warn("用户{}重新连接，准备关闭旧连接：oldChannelId={}, newChannelId={}", 
                userId, oldChannelId, channelId);
            
            // 【重要】先从映射中移除，再关闭Channel
            // 这样channelInactive触发时，不会清除新连接的状态
            userIdChannelMap.remove(userId, oldChannel); // 原子性操作
            channelIdUserIdMap.remove(oldChannelId);
            
            // 从多连接映射中移除旧channelId
            userChannels.remove(oldChannelId);
            
            // 关闭旧连接（异步，不影响新连接）
            if (oldChannel.isActive()) {
                oldChannel.close().addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("旧连接已关闭：userId={}, oldChannelId={}", userId, oldChannelId);
                    }
                });
            }
        }
        
        // 添加新连接
        userIdChannelMap.put(userId, channel);
        channelIdUserIdMap.put(channelId, userId);
        userConnectTimeMap.put(userId, System.currentTimeMillis());
        userChannels.add(channelId);
        
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        
        log.info("用户{}连接添加成功，channelId：{}，当前活跃连接数：{}", 
            userId, channelId, activeConnections.get());
        
        return true;
    }

    /**
     * 移除用户连接
     */
    public static void removeUserChannel(String userId) {
        if (userId == null) {
            return;
        }
        
        Channel channel = userIdChannelMap.remove(userId);
        if (channel != null) {
            String channelId = channel.id().asLongText();
            channelIdUserIdMap.remove(channelId);
            userConnectTimeMap.remove(userId);
            
            // 从多连接映射中移除
            Set<String> userChannels = userMultiChannelMap.get(userId);
            if (userChannels != null) {
                userChannels.remove(channelId);
                if (userChannels.isEmpty()) {
                    userMultiChannelMap.remove(userId);
                }
            }
            
            activeConnections.decrementAndGet();
            
            // 计算连接时长
            Long connectTime = userConnectTimeMap.get(userId);
            if (connectTime != null) {
                long duration = System.currentTimeMillis() - connectTime;
                log.info("用户{}连接移除，channelId：{}，连接时长：{}ms，当前活跃连接数：{}", 
                    userId, channelId, duration, activeConnections.get());
            } else {
                log.info("用户{}连接移除，channelId：{}，当前活跃连接数：{}", 
                    userId, channelId, activeConnections.get());
            }
        }
    }

    /**
     * 根据channelId移除连接
     */
    public static void removeChannelById(String channelId) {
        if (channelId == null) {
            return;
        }
        
        String userId = channelIdUserIdMap.get(channelId);
        if (userId != null) {
            removeUserChannel(userId);
        }
    }

    /**
     * 根据用户id获取channel
     */
    public static Channel getChannelByUserId(String userId) {
        if (userId == null) {
            return null;
        }
        
        Channel channel = userIdChannelMap.get(userId);
        
        // 检查channel是否仍然活跃
        if (channel != null && !channel.isActive()) {
            log.warn("用户{}的连接已不活跃，自动移除", userId);
            removeUserChannel(userId);
            return null;
        }
        
        return channel;
    }

    /**
     * 获取所有在线用户ID
     */
    public static Set<String> getAllOnLineUserId() {
        // 清理无效连接后返回
        cleanupInactiveChannels();
        return userIdChannelMap.keySet();
    }

    /**
     * 根据channelId获取用户id
     */
    public static String getUserIdByChannelId(String channelId) {
        return channelIdUserIdMap.get(channelId);
    }

    /**
     * 检查用户是否在线
     */
    public static boolean isUserOnline(String userId) {
        Channel channel = getChannelByUserId(userId);
        return channel != null && channel.isActive();
    }

    /**
     * 获取用户连接时长（毫秒）
     */
    public static long getUserConnectionDuration(String userId) {
        Long connectTime = userConnectTimeMap.get(userId);
        if (connectTime != null) {
            return System.currentTimeMillis() - connectTime;
        }
        return 0;
    }

    /**
     * 获取活跃连接数
     */
    public static int getActiveConnectionCount() {
        return activeConnections.get();
    }

    /**
     * 获取总连接数（包括历史连接）
     */
    public static int getTotalConnectionCount() {
        return totalConnections.get();
    }

    /**
     * 定时清理无效连接
     */
    private static void cleanupInactiveChannels() {
        try {
            int cleanedCount = 0;
            
            // 检查并清理无效的用户连接
            for (ConcurrentMap.Entry<String, Channel> entry : userIdChannelMap.entrySet()) {
                Channel channel = entry.getValue();
                if (channel == null || !channel.isActive()) {
                    String userId = entry.getKey();
                    log.debug("清理无效连接：用户{}", userId);
                    removeUserChannel(userId);
                    cleanedCount++;
                }
            }
            
            // 清理孤儿channelId映射
            channelIdUserIdMap.entrySet().removeIf(entry -> {
                String userId = entry.getValue();
                return !userIdChannelMap.containsKey(userId);
            });
            
            if (cleanedCount > 0) {
                log.info("清理无效连接完成，清理数量：{}", cleanedCount);
            }
            
        } catch (Exception e) {
            log.error("清理无效连接时发生异常", e);
        }
    }

    /**
     * 输出连接统计信息
     */
    private static void logConnectionStats() {
        try {
            int active = activeConnections.get();
            int total = totalConnections.get();
            int mapSize = userIdChannelMap.size();
            
            if (active > 0) {
                log.info("连接统计 - 活跃连接: {}, 总连接数: {}, Map大小: {}", active, total, mapSize);
            }
            
        } catch (Exception e) {
            log.error("输出连接统计信息时发生异常", e);
        }
    }

    /**
     * 强制断开所有连接（用于服务关闭）
     */
    @PreDestroy
    public static void closeAllConnections() {
        log.info("开始关闭所有连接，当前连接数：{}", activeConnections.get());
        
        try {
            for (Channel channel : userIdChannelMap.values()) {
                if (channel != null && channel.isActive()) {
                    channel.close();
                }
            }
            
            // 清空所有映射
            userIdChannelMap.clear();
            channelIdUserIdMap.clear();
            userConnectTimeMap.clear();
            userMultiChannelMap.clear();
            
            activeConnections.set(0);
            
            log.info("所有连接已关闭");
            
        } catch (Exception e) {
            log.error("关闭连接时发生异常", e);
        } finally {
            // 关闭清理线程池
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取详细的连接信息（用于监控和调试）
     */
    public static String getConnectionDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 连接管理器状态 ===\n");
        sb.append("活跃连接数: ").append(activeConnections.get()).append("\n");
        sb.append("总连接数: ").append(totalConnections.get()).append("\n");
        sb.append("用户映射数量: ").append(userIdChannelMap.size()).append("\n");
        sb.append("通道映射数量: ").append(channelIdUserIdMap.size()).append("\n");
        sb.append("多连接映射数量: ").append(userMultiChannelMap.size()).append("\n");
        
        if (log.isDebugEnabled()) {
            sb.append("\n在线用户: ");
            userIdChannelMap.keySet().forEach(userId -> {
                long duration = getUserConnectionDuration(userId);
                sb.append(userId).append("(").append(duration).append("ms) ");
            });
        }
        
        return sb.toString();
    }
}