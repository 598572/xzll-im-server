package com.xzll.connect.netty.heart;

import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.config.IMConnectServerConfig;
import com.xzll.connect.service.UserStatusManagerService;
import com.xzll.common.utils.RedissonUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Netty服务器心跳处理实现
 * 优化内容：
 * 1. 智能心跳检测，减少无效检测
 * 2. 支持主动心跳发送
 * 3. 心跳失败重试机制
 * 4. 心跳统计和监控
 * 5. 优化心跳间隔策略
 * 
 * @Author: hzz
 * @Date: 2024/6/1 17:30:01
 * @Description:
 */
@Slf4j
@Service
public class NettyServerHeartBeatHandlerImpl implements HeartBeatHandler {

    @Resource
    private IMConnectServerConfig imConnectServerConfig;
    
    @Resource
    private RedissonUtils redissonUtils;
    
    @Resource
    private UserStatusManagerService userStatusManagerService;

    // 存储每个连接的心跳失败次数
    private static final ConcurrentHashMap<String, Integer> heartbeatFailureCount = new ConcurrentHashMap<>();
    
    // 存储每个连接的主动心跳任务
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> activeHeartbeatTasks = new ConcurrentHashMap<>();

    @Override
    public void process(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        String userId = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
        
        try {
            // 获取配置的心跳超时时间
            long heartBeatTimeMs = imConnectServerConfig.getHeartBeatTime() * 1000;
            
            // 获取最后读取时间
            Long lastReadTime = NettyAttrUtil.getReaderTime(ctx.channel());
            long currentTime = System.currentTimeMillis();
            
            if (lastReadTime == null) {
                // 如果没有读取时间记录，设置当前时间并返回
                NettyAttrUtil.updateReaderTime(ctx.channel(), currentTime);
                log.debug("首次心跳检测，设置读取时间：channelId={}, userId={}", channelId, userId);
                return;
            }
            
            long timeSinceLastRead = currentTime - lastReadTime;
            
            // 检查是否超时
            if (timeSinceLastRead > heartBeatTimeMs) {
                handleHeartbeatTimeout(ctx, userId, channelId, timeSinceLastRead);
            } else {
                // 重置失败计数
                heartbeatFailureCount.remove(channelId);
                
                // 如果距离超时还有一段时间，可以主动发送ping
                long timeUntilTimeout = heartBeatTimeMs - timeSinceLastRead;
                if (timeUntilTimeout < heartBeatTimeMs / 2) {
                    sendActiveHeartbeat(ctx, userId, channelId);
                }
                
                log.debug("心跳检测正常：channelId={}, userId={}, 距离上次读取={}ms", 
                    channelId, userId, timeSinceLastRead);
            }
            
        } catch (Exception e) {
            log.error("心跳检测异常：channelId={}, userId={}", channelId, userId, e);
        }
    }

    /**
     * 处理心跳超时
     */
    private void handleHeartbeatTimeout(ChannelHandlerContext ctx, String userId, String channelId, long timeSinceLastRead) {
        // 获取配置的最大失败次数
        int maxFailures = imConnectServerConfig.getMaxHeartbeatFailures();
        
        // 增加失败计数
        int failureCount = heartbeatFailureCount.getOrDefault(channelId, 0) + 1;
        heartbeatFailureCount.put(channelId, failureCount);
        
        log.warn("心跳超时检测：channelId={}, userId={}, 超时时长={}ms, 失败次数={}/{}", 
            channelId, userId, timeSinceLastRead, failureCount, maxFailures);
        
        if (failureCount >= maxFailures) {
            // 超过最大失败次数，关闭连接
            closeConnectionDueToHeartbeatFailure(ctx, userId, channelId, timeSinceLastRead);
        } else {
            // 尝试主动发送心跳
            sendActiveHeartbeat(ctx, userId, channelId);
        }
    }

    /**
     * 由于心跳失败关闭连接
     */
    private void closeConnectionDueToHeartbeatFailure(ChannelHandlerContext ctx, String userId, String channelId, long timeSinceLastRead) {
        int maxFailures = imConnectServerConfig.getMaxHeartbeatFailures();
        if (StringUtils.isNotBlank(userId)) {
            log.warn("客户端[{}]心跳超时[{}]ms，连续失败{}次，关闭连接！channelId={}", 
                userId, timeSinceLastRead, maxFailures, channelId);
        } else {
            log.warn("未认证客户端心跳超时[{}]ms，关闭连接！channelId={}", 
                timeSinceLastRead, channelId);
        }
        
        // 清理相关数据
        cleanup(channelId);
        
        // 关闭连接
        ctx.channel().close();
    }

    /**
     * 发送主动心跳
     */
    private void sendActiveHeartbeat(ChannelHandlerContext ctx, String userId, String channelId) {
        try {
            if (ctx.channel().isActive()) {
                // 发送Ping帧
                ctx.writeAndFlush(new PingWebSocketFrame())
                    .addListener(future -> {
                        if (future.isSuccess()) {
                            log.debug("主动心跳发送成功：channelId={}, userId={}", channelId, userId);
                        } else {
                            log.warn("主动心跳发送失败：channelId={}, userId={}", channelId, userId, future.cause());
                        }
                    });
            }
        } catch (Exception e) {
            log.error("发送主动心跳异常：channelId={}, userId={}", channelId, userId, e);
        }
    }

    /**
     * 启动主动心跳任务（可选功能，在连接建立后调用）
     */
    public void startActiveHeartbeat(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        String userId = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
        
        // 如果已经有任务在运行，先停止
        stopActiveHeartbeat(channelId);
        
        // 获取配置的主动心跳间隔
        int heartbeatInterval = imConnectServerConfig.getActiveHeartbeatInterval();
        
        // 启动新的主动心跳任务
        ScheduledFuture<?> task = ctx.channel().eventLoop().scheduleAtFixedRate(
            () -> {
                if (ctx.channel().isActive()) {
                    sendActiveHeartbeat(ctx, userId, channelId);
                } else {
                    stopActiveHeartbeat(channelId);
                }
            },
            heartbeatInterval,
            heartbeatInterval,
            TimeUnit.SECONDS
        );
        
        activeHeartbeatTasks.put(channelId, task);
        log.debug("启动主动心跳任务：channelId={}, userId={}, 间隔={}秒", 
            channelId, userId, heartbeatInterval);
    }

    /**
     * 停止主动心跳任务
     */
    public void stopActiveHeartbeat(String channelId) {
        ScheduledFuture<?> task = activeHeartbeatTasks.remove(channelId);
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            log.debug("停止主动心跳任务：channelId={}", channelId);
        }
    }

    /**
     * 记录心跳响应
     * 在以下场景调用：
     * 1. 收到客户端主动发送的Ping时
     * 2. 收到客户端回复的Pong时（服务端发送Ping后）
     * 
     * @param ctx ChannelHandlerContext
     * @param heartbeatType 心跳类型："ping" 表示客户端主动发送的ping，"pong" 表示客户端回复的pong
     */
    public void recordHeartbeatResponse(ChannelHandlerContext ctx, String heartbeatType) {
        String channelId = ctx.channel().id().asLongText();
        String userId = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
        
        // 更新读取时间
        NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
        
        // 重置失败计数
        heartbeatFailureCount.remove(channelId);
        
        // 【重要】检查并恢复Redis中的在线状态（修复重连后状态不一致问题）
        if (StringUtils.isNotBlank(userId)) {
            checkAndRestoreOnlineStatus(userId);
        }
        
        // 根据心跳类型输出不同的日志
        if ("ping".equalsIgnoreCase(heartbeatType)) {
            log.debug("收到客户端主动心跳ping：channelId={}, userId={}", channelId, userId);
        } else if ("pong".equalsIgnoreCase(heartbeatType)) {
            log.debug("收到客户端心跳响应pong：channelId={}, userId={}", channelId, userId);
        } else {
            log.debug("收到心跳消息：channelId={}, userId={}, 类型={}", channelId, userId, heartbeatType);
        }
    }
    
    /**
     * 记录心跳响应（兼容旧版本，默认类型为"unknown"）
     * @deprecated 建议使用 recordHeartbeatResponse(ctx, heartbeatType) 明确指定心跳类型
     */
    @Deprecated
    public void recordHeartbeatResponse(ChannelHandlerContext ctx) {
        recordHeartbeatResponse(ctx, "unknown");
    }

    /**
     * 清理连接相关的心跳数据
     */
    public void cleanup(String channelId) {
        heartbeatFailureCount.remove(channelId);
        stopActiveHeartbeat(channelId);
        
        log.debug("清理心跳数据：channelId={}", channelId);
    }

    /**
     * 获取连接的心跳失败次数
     */
    public int getHeartbeatFailureCount(String channelId) {
        return heartbeatFailureCount.getOrDefault(channelId, 0);
    }

    /**
     * 获取心跳统计信息
     */
    public String getHeartbeatStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 心跳统计信息 ===\n");
        sb.append("活跃心跳任务数: ").append(activeHeartbeatTasks.size()).append("\n");
        sb.append("心跳失败连接数: ").append(heartbeatFailureCount.size()).append("\n");
        sb.append("心跳超时时间: ").append(imConnectServerConfig.getHeartBeatTime()).append("秒\n");
        sb.append("最大失败次数: ").append(imConnectServerConfig.getMaxHeartbeatFailures()).append("\n");
        sb.append("主动心跳间隔: ").append(imConnectServerConfig.getActiveHeartbeatInterval()).append("秒\n");
        
        if (!heartbeatFailureCount.isEmpty()) {
            sb.append("\n失败连接详情:\n");
            heartbeatFailureCount.forEach((channelId, count) -> {
                sb.append("  ChannelId: ").append(channelId)
                  .append(", 失败次数: ").append(count).append("\n");
            });
        }
        
        return sb.toString();
    }

    /**
     * 检查连接的心跳健康状态
     */
    public boolean isHeartbeatHealthy(String channelId) {
        int failureCount = heartbeatFailureCount.getOrDefault(channelId, 0);
        int maxFailures = imConnectServerConfig.getMaxHeartbeatFailures();
        return failureCount < maxFailures;
    }

    /**
     * 检查并恢复Redis中的在线状态
     * 解决用户重连后，本地Channel存在但Redis状态为null的问题
     * 
     * @param userId 用户ID
     */
    private void checkAndRestoreOnlineStatus(String userId) {
        try {
            // 检查Redis中的在线状态
            String userStatus = redissonUtils.getHash(
                ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, userId);
            
            // 如果Redis中没有在线状态，说明可能是重连导致的状态丢失，需要恢复
            if (StringUtils.isBlank(userStatus)) {
                log.warn("检测到用户{}的Redis在线状态丢失，正在恢复...", userId);
                
                // 恢复在线状态
                userStatusManagerService.userConnectSuccessAfter(
                    ImConstant.UserStatus.ON_LINE.getValue(), userId);
                
                log.info("已恢复用户{}的Redis在线状态", userId);
            }
        } catch (Exception e) {
            log.error("检查并恢复用户{}的在线状态失败", userId, e);
        }
    }
    
    /**
     * 强制清理所有心跳数据（用于服务关闭）
     */
    public void cleanupAll() {
        log.info("清理所有心跳数据，任务数：{}，失败记录数：{}", 
            activeHeartbeatTasks.size(), heartbeatFailureCount.size());
        
        // 取消所有主动心跳任务
        activeHeartbeatTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel(false);
            }
        });
        
        activeHeartbeatTasks.clear();
        heartbeatFailureCount.clear();
        
        log.info("心跳数据清理完成");
    }
}