package com.xzll.connect.netty.handler;

import com.xzll.common.utils.RedissonUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * 连接限制处理器 - Redis分布式版本
 * 功能：
 * 1. 限制单个IP的最大连接数（分布式）
 * 2. 限制全局最大连接数（分布式）
 * 3. 连接频率限制（分布式）
 * 4. 支持分布式部署，所有数据存储在Redis中
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    // ============= Redis Key 前缀定义 =============
    private static final String IP_CONNECTION_COUNT_KEY_PREFIX = "im:limit:conn:";
    private static final String IP_CONNECTION_RATE_KEY_PREFIX = "im:limit:rate:";
    private static final String GLOBAL_CONNECTION_COUNT_KEY = "im:limit:global:count";
    private static final String IP_BLOCKED_KEY_PREFIX = "im:limit:blocked:";
    
    // ============= 配置参数 =============
    
    // 单个IP最大连接数
    @Value("${im.netty.security.max-connections-per-ip:1000}")
    private int maxConnectionsPerIp;
    
    // 全局最大连接数
    @Value("${im.netty.security.max-total-connections:10000}")
    private int maxTotalConnections;
    
    // 连接频率限制（每分钟）
    @Value("${im.netty.security.max-connections-per-minute:6000}")
    private int maxConnectionsPerMinute;
    
    // 注入RedissonUtils，避免静态初始化问题
    @Autowired
    private RedissonUtils redissonUtils;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String clientIp = getClientIp(ctx);
        
        try {
            // 检查IP是否被手动封禁
            if (isIpBlocked(clientIp)) {
                log.warn("IP已被封禁，拒绝连接：{}", clientIp);
                ctx.close();
                return;
            }
            
            // 检查全局连接数限制
            if (!checkGlobalConnectionLimit()) {
                log.warn("全局连接数超过限制，拒绝连接：{}, 限制：{}", clientIp, maxTotalConnections);
                ctx.close();
                return;
            }
            
            // 检查单IP连接数限制
            if (!checkIpConnectionLimit(clientIp)) {
                log.warn("IP连接数超过限制，拒绝连接：{}, 限制：{}", clientIp, maxConnectionsPerIp);
                ctx.close();
                return;
            }
            
            // 检查连接频率限制
            if (!checkIpConnectionRate(clientIp)) {
                log.warn("IP连接频率超过限制，拒绝连接：{}, 限制：{}/分钟", clientIp, maxConnectionsPerMinute);
                ctx.close();
                return;
            }
            
            // 通过所有检查，增加计数器
            incrementConnectionCounters(clientIp);
            
            log.debug("连接通过限制检查：{}", clientIp);
            
        } catch (Exception e) {
            log.error("连接限制检查异常：{}", clientIp, e);
            // 异常时为了安全起见，拒绝连接
            ctx.close();
            return;
        }
        
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientIp = getClientIp(ctx);
        
        try {
            // 减少连接计数
            decrementConnectionCounters(clientIp);
            log.debug("连接断开，更新计数器：{}", clientIp);
            
        } catch (Exception e) {
            log.error("更新连接计数器异常：{}", clientIp, e);
        }
        
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String clientIp = getClientIp(ctx);
        log.error("连接限制处理器异常：{}", clientIp, cause);
        ctx.close();
    }

    // ============= Redis 分布式限制功能 =============

    /**
     * 检查全局连接数限制
     */
    private boolean checkGlobalConnectionLimit() {
        try {
            String countStr = redissonUtils.getString(GLOBAL_CONNECTION_COUNT_KEY);
            int currentCount = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            
            return currentCount < maxTotalConnections;
        } catch (Exception e) {
            log.error("检查全局连接数限制异常", e);
            return true; // 异常时不限制，避免误伤
        }
    }

    /**
     * 检查IP连接数限制
     */
    private boolean checkIpConnectionLimit(String ip) {
        try {
            String connectionKey = IP_CONNECTION_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(connectionKey);
            int currentCount = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            
            return currentCount < maxConnectionsPerIp;
        } catch (Exception e) {
            log.error("检查IP连接数限制异常：{}", ip, e);
            return true; // 异常时不限制，避免误伤
        }
    }

    /**
     * 检查IP连接频率限制
     */
    private boolean checkIpConnectionRate(String ip) {
        try {
            String rateKey = IP_CONNECTION_RATE_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(rateKey);
            int currentRate = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            
            return currentRate < maxConnectionsPerMinute;
        } catch (Exception e) {
            log.error("检查IP连接频率限制异常：{}", ip, e);
            return true; // 异常时不限制，避免误伤
        }
    }

    /**
     * 增加连接计数器
     */
    private void incrementConnectionCounters(String ip) {
        try {
            // 增加全局连接计数
            String globalCountStr = redissonUtils.getString(GLOBAL_CONNECTION_COUNT_KEY);
            int globalCount = StringUtils.isNotBlank(globalCountStr) ? Integer.parseInt(globalCountStr) : 0;
            redissonUtils.setString(GLOBAL_CONNECTION_COUNT_KEY, String.valueOf(globalCount + 1));
            
            // 增加IP连接计数（设置较长的过期时间，防止内存泄漏）
            String connectionKey = IP_CONNECTION_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(connectionKey);
            int count = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            redissonUtils.setString(connectionKey, String.valueOf(count + 1), 1, TimeUnit.HOURS);
            
            // 增加IP连接频率计数（1分钟过期）
            String rateKey = IP_CONNECTION_RATE_KEY_PREFIX + ip;
            String rateStr = redissonUtils.getString(rateKey);
            int rate = StringUtils.isNotBlank(rateStr) ? Integer.parseInt(rateStr) : 0;
            redissonUtils.setString(rateKey, String.valueOf(rate + 1), 1, TimeUnit.MINUTES);
            
        } catch (Exception e) {
            log.error("增加连接计数器异常：{}", ip, e);
        }
    }

    /**
     * 减少连接计数器
     */
    private void decrementConnectionCounters(String ip) {
        try {
            // 减少全局连接计数
            String globalCountStr = redissonUtils.getString(GLOBAL_CONNECTION_COUNT_KEY);
            int globalCount = StringUtils.isNotBlank(globalCountStr) ? Integer.parseInt(globalCountStr) : 0;
            if (globalCount > 0) {
                redissonUtils.setString(GLOBAL_CONNECTION_COUNT_KEY, String.valueOf(globalCount - 1));
            }
            
            // 减少IP连接计数
            String connectionKey = IP_CONNECTION_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(connectionKey);
            int count = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            if (count > 0) {
                if (count == 1) {
                    // 如果计数为1，直接删除key
                    redissonUtils.deleteString(connectionKey);
                } else {
                    redissonUtils.setString(connectionKey, String.valueOf(count - 1), 1, TimeUnit.HOURS);
                }
            }
            
        } catch (Exception e) {
            log.error("减少连接计数器异常：{}", ip, e);
        }
    }

    /**
     * 检查IP是否被封禁
     */
    private boolean isIpBlocked(String ip) {
        try {
            String blockedKey = IP_BLOCKED_KEY_PREFIX + ip;
            String blockedValue = redissonUtils.getString(blockedKey);
            return StringUtils.isNotBlank(blockedValue);
        } catch (Exception e) {
            log.error("检查IP封禁状态异常：{}", ip, e);
            return false;
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            return socketAddress.getAddress().getHostAddress();
        } catch (Exception e) {
            log.warn("获取客户端IP失败", e);
            return "unknown";
        }
    }

    // ============= 静态方法需要懒加载获取RedissonUtils =============

    /**
     * 懒加载获取RedissonUtils Bean
     */
    private static RedissonUtils getRedissonUtils() {
        try {
            return cn.hutool.extra.spring.SpringUtil.getBean(RedissonUtils.class);
        } catch (Exception e) {
            log.error("无法获取RedissonUtils Bean", e);
            return null;
        }
    }

    // ============= 分布式管理接口（静态方法修复版本） =============

    /**
     * 获取全局连接数
     */
    public static int getTotalConnections() {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return 0;
            
            String countStr = redissonUtils.getString(GLOBAL_CONNECTION_COUNT_KEY);
            return StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("获取全局连接数异常", e);
            return 0;
        }
    }

    /**
     * 获取指定IP的连接数
     */
    public static int getIpConnectionCount(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return 0;
            
            String connectionKey = IP_CONNECTION_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(connectionKey);
            return StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("获取IP连接数异常：{}", ip, e);
            return 0;
        }
    }

    /**
     * 获取指定IP的连接频率
     */
    public static int getIpConnectionRate(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return 0;
            
            String rateKey = IP_CONNECTION_RATE_KEY_PREFIX + ip;
            String rateStr = redissonUtils.getString(rateKey);
            return StringUtils.isNotBlank(rateStr) ? Integer.parseInt(rateStr) : 0;
        } catch (Exception e) {
            log.error("获取IP连接频率异常：{}", ip, e);
            return 0;
        }
    }

    /**
     * 手动封禁IP
     */
    public static void blockIp(String ip, int durationMinutes) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，封禁IP失败：{}", ip);
                return;
            }
            
            String blockedKey = IP_BLOCKED_KEY_PREFIX + ip;
            String blockedValue = String.valueOf(System.currentTimeMillis());
            
            if (durationMinutes > 0) {
                redissonUtils.setString(blockedKey, blockedValue, durationMinutes, TimeUnit.MINUTES);
            } else {
                // 永久封禁
                redissonUtils.setString(blockedKey, blockedValue);
            }
            
            log.info("手动封禁IP: {}, 时长: {}分钟", ip, durationMinutes > 0 ? durationMinutes : "永久");
        } catch (Exception e) {
            log.error("封禁IP异常：{}", ip, e);
        }
    }

    /**
     * 解封IP
     */
    public static void unblockIp(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，解封IP失败：{}", ip);
                return;
            }
            
            String blockedKey = IP_BLOCKED_KEY_PREFIX + ip;
            String connectionKey = IP_CONNECTION_COUNT_KEY_PREFIX + ip;
            String rateKey = IP_CONNECTION_RATE_KEY_PREFIX + ip;
            
            redissonUtils.deleteString(blockedKey);
            redissonUtils.deleteString(connectionKey);
            redissonUtils.deleteString(rateKey);
            
            log.info("解封IP: {}", ip);
        } catch (Exception e) {
            log.error("解封IP异常：{}", ip, e);
        }
    }

    /**
     * 重置全局连接计数（慎用，仅用于故障恢复）
     */
    public static void resetGlobalConnectionCount() {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，重置全局连接计数失败");
                return;
            }
            
            redissonUtils.deleteString(GLOBAL_CONNECTION_COUNT_KEY);
            log.info("全局连接计数已重置");
        } catch (Exception e) {
            log.error("重置全局连接计数异常", e);
        }
    }

    /**
     * 获取连接统计信息
     */
    public static String getConnectionStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 分布式连接限制统计 ===\n");
        sb.append("全局连接数: ").append(getTotalConnections()).append("\n");
        sb.append("所有连接数据存储在Redis中，支持分布式部署\n");
        sb.append("Redis Key前缀:\n");
        sb.append("  IP连接数: ").append(IP_CONNECTION_COUNT_KEY_PREFIX).append("\n");
        sb.append("  IP频率: ").append(IP_CONNECTION_RATE_KEY_PREFIX).append("\n");
        sb.append("  全局计数: ").append(GLOBAL_CONNECTION_COUNT_KEY).append("\n");
        sb.append("  IP封禁: ").append(IP_BLOCKED_KEY_PREFIX).append("\n");
        
        return sb.toString();
    }
}