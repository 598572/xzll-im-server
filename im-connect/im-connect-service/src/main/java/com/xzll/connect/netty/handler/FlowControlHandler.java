package com.xzll.connect.netty.handler;

import com.xzll.common.utils.RedissonUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * 流量控制处理器 - Redis分布式版本
 * 功能：
 * 1. 消息频率限制（分布式）
 * 2. 消息大小限制（分布式）
 * 3. 带宽控制（分布式）
 * 4. 自动限流和恢复（分布式）
 * 5. 支持分布式部署，所有数据存储在Redis中
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class FlowControlHandler extends ChannelInboundHandlerAdapter {

    // ============= Redis Key 前缀定义 =============
    private static final String IP_MESSAGE_COUNT_KEY_PREFIX = "im:flow:msg:";
    private static final String IP_BYTE_COUNT_KEY_PREFIX = "im:flow:byte:";
    private static final String IP_THROTTLED_KEY_PREFIX = "im:flow:throttled:";
    
    // ============= 配置参数 =============
    
    // 每秒最大消息数
    @Value("${im.netty.flow-control.max-messages-per-second:10}")
    private int maxMessagesPerSecond;
    
    // 单条消息最大字节数
    @Value("${im.netty.flow-control.max-message-size:8192}")
    private int maxMessageSize;
    
    // 每秒最大字节数（带宽控制）
    @Value("${im.netty.flow-control.max-bytes-per-second:102400}")
    private long maxBytesPerSecond;
    
    // 限流时间（分钟）
    @Value("${im.netty.flow-control.throttle-duration-minutes:1}")
    private int throttleDurationMinutes;
    
    // 注入RedissonUtils，避免静态初始化问题
    @Autowired
    private RedissonUtils redissonUtils;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String clientIp = getClientIp(ctx);
        
        // 检查是否被限流
        if (isThrottled(clientIp)) {
            log.debug("IP被限流，丢弃消息：{}", clientIp);
            return;
        }
        
        if (msg instanceof TextWebSocketFrame) {
            TextWebSocketFrame frame = (TextWebSocketFrame) msg;
            String text = frame.text();
            int messageSize = text.getBytes().length;
            
            try {
                // 检查消息大小限制
                if (messageSize > maxMessageSize) {
                    log.warn("消息大小超过限制，限流处理：IP={}, 大小={}字节, 限制={}字节", 
                        clientIp, messageSize, maxMessageSize);
                    throttleIp(clientIp, "消息大小超限");
                    return;
                }
                
                // 检查消息频率限制
                if (!checkMessageFrequency(clientIp)) {
                    log.warn("消息频率超过限制，限流处理：IP={}, 限制={}/秒", clientIp, maxMessagesPerSecond);
                    throttleIp(clientIp, "消息频率超限");
                    return;
                }
                
                // 检查带宽限制
                if (!checkBandwidthLimit(clientIp, messageSize)) {
                    log.warn("带宽超过限制，限流处理：IP={}, 限制={}字节/秒", clientIp, maxBytesPerSecond);
                    throttleIp(clientIp, "带宽超限");
                    return;
                }
                
                // 更新计数器
                updateCounters(clientIp, messageSize);
                
                log.debug("消息通过流控检查：IP={}, 大小={}字节", clientIp, messageSize);
                
            } catch (Exception e) {
                log.error("流量控制检查异常：IP={}", clientIp, e);
                // 异常时为了安全起见，限流处理
                throttleIp(clientIp, "流控检查异常");
                return;
            }
        }
        
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String clientIp = getClientIp(ctx);
        log.error("流量控制处理器异常：{}", clientIp, cause);
        super.exceptionCaught(ctx, cause);
    }

    // ============= Redis 分布式流控功能 =============

    /**
     * 检查消息频率限制
     */
    private boolean checkMessageFrequency(String ip) {
        try {
            String messageKey = IP_MESSAGE_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(messageKey);
            int currentCount = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            
            return currentCount < maxMessagesPerSecond;
        } catch (Exception e) {
            log.error("检查消息频率限制异常：{}", ip, e);
            return false; // 异常时限制，保护系统
        }
    }

    /**
     * 检查带宽限制
     */
    private boolean checkBandwidthLimit(String ip, int messageSize) {
        try {
            String byteKey = IP_BYTE_COUNT_KEY_PREFIX + ip;
            String byteStr = redissonUtils.getString(byteKey);
            long currentBytes = StringUtils.isNotBlank(byteStr) ? Long.parseLong(byteStr) : 0;
            
            return (currentBytes + messageSize) <= maxBytesPerSecond;
        } catch (Exception e) {
            log.error("检查带宽限制异常：{}", ip, e);
            return false; // 异常时限制，保护系统
        }
    }

    /**
     * 更新计数器
     */
    private void updateCounters(String ip, int messageSize) {
        try {
            // 更新消息计数（每秒重置）
            String messageKey = IP_MESSAGE_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(messageKey);
            int count = StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
            redissonUtils.setString(messageKey, String.valueOf(count + 1), 1, TimeUnit.SECONDS);
            
            // 更新字节计数（每秒重置）
            String byteKey = IP_BYTE_COUNT_KEY_PREFIX + ip;
            String byteStr = redissonUtils.getString(byteKey);
            long bytes = StringUtils.isNotBlank(byteStr) ? Long.parseLong(byteStr) : 0;
            redissonUtils.setString(byteKey, String.valueOf(bytes + messageSize), 1, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("更新流控计数器异常：{}", ip, e);
        }
    }

    /**
     * 检查IP是否被限流
     */
    private boolean isThrottled(String ip) {
        try {
            String throttledKey = IP_THROTTLED_KEY_PREFIX + ip;
            String throttledValue = redissonUtils.getString(throttledKey);
            return StringUtils.isNotBlank(throttledValue);
        } catch (Exception e) {
            log.error("检查IP限流状态异常：{}", ip, e);
            return false;
        }
    }

    /**
     * 限流指定IP
     */
    private void throttleIp(String ip, String reason) {
        try {
            String throttledKey = IP_THROTTLED_KEY_PREFIX + ip;
            String throttledValue = reason + ":" + System.currentTimeMillis();
            
            redissonUtils.setString(throttledKey, throttledValue, throttleDurationMinutes, TimeUnit.MINUTES);
            log.info("IP被限流：{}, 原因：{}, 限流时长：{}分钟", ip, reason, throttleDurationMinutes);
        } catch (Exception e) {
            log.error("限流IP异常：{}", ip, e);
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
     * 手动解除IP限流
     */
    public static void unthrottleIp(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，解除IP限流失败：{}", ip);
                return;
            }
            
            String throttledKey = IP_THROTTLED_KEY_PREFIX + ip;
            String messageKey = IP_MESSAGE_COUNT_KEY_PREFIX + ip;
            String byteKey = IP_BYTE_COUNT_KEY_PREFIX + ip;
            
            redissonUtils.deleteString(throttledKey);
            redissonUtils.deleteString(messageKey);
            redissonUtils.deleteString(byteKey);
            
            log.info("手动解除IP限流: {}", ip);
        } catch (Exception e) {
            log.error("解除IP限流异常：{}", ip, e);
        }
    }

    /**
     * 手动限流IP
     */
    public static void throttleIp(String ip, String reason, int durationMinutes) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，限流IP失败：{}", ip);
                return;
            }
            
            String throttledKey = IP_THROTTLED_KEY_PREFIX + ip;
            String throttledValue = reason + ":" + System.currentTimeMillis();
            
            if (durationMinutes > 0) {
                redissonUtils.setString(throttledKey, throttledValue, durationMinutes, TimeUnit.MINUTES);
            } else {
                // 永久限流
                redissonUtils.setString(throttledKey, throttledValue);
            }
            
            log.info("手动限流IP: {}, 原因: {}, 时长: {}分钟", ip, reason, durationMinutes > 0 ? durationMinutes : "永久");
        } catch (Exception e) {
            log.error("手动限流IP异常：{}", ip, e);
        }
    }

    /**
     * 获取IP的消息频率
     */
    public static int getIpMessageRate(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return 0;
            
            String messageKey = IP_MESSAGE_COUNT_KEY_PREFIX + ip;
            String countStr = redissonUtils.getString(messageKey);
            return StringUtils.isNotBlank(countStr) ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("获取IP消息频率异常：{}", ip, e);
            return 0;
        }
    }

    /**
     * 获取IP的带宽使用
     */
    public static long getIpBandwidthUsage(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return 0;
            
            String byteKey = IP_BYTE_COUNT_KEY_PREFIX + ip;
            String byteStr = redissonUtils.getString(byteKey);
            return StringUtils.isNotBlank(byteStr) ? Long.parseLong(byteStr) : 0;
        } catch (Exception e) {
            log.error("获取IP带宽使用异常：{}", ip, e);
            return 0;
        }
    }

    /**
     * 检查IP是否被限流（公共接口）
     */
    public static boolean isIpThrottled(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return false;
            
            String throttledKey = IP_THROTTLED_KEY_PREFIX + ip;
            String throttledValue = redissonUtils.getString(throttledKey);
            return StringUtils.isNotBlank(throttledValue);
        } catch (Exception e) {
            log.error("检查IP限流状态异常：{}", ip, e);
            return false;
        }
    }

    /**
     * 获取IP限流原因
     */
    public static String getThrottleReason(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return null;
            
            String throttledKey = IP_THROTTLED_KEY_PREFIX + ip;
            String throttledValue = redissonUtils.getString(throttledKey);
            
            if (StringUtils.isNotBlank(throttledValue)) {
                String[] parts = throttledValue.split(":");
                return parts.length > 0 ? parts[0] : "未知原因";
            }
            
            return null;
        } catch (Exception e) {
            log.error("获取IP限流原因异常：{}", ip, e);
            return null;
        }
    }

    /**
     * 获取流控统计信息
     */
    public static String getFlowControlStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 分布式流量控制统计 ===\n");
        sb.append("所有流控数据存储在Redis中，支持分布式部署\n");
        sb.append("Redis Key前缀:\n");
        sb.append("  消息计数: ").append(IP_MESSAGE_COUNT_KEY_PREFIX).append("\n");
        sb.append("  字节计数: ").append(IP_BYTE_COUNT_KEY_PREFIX).append("\n");
        sb.append("  限流记录: ").append(IP_THROTTLED_KEY_PREFIX).append("\n");
        
        return sb.toString();
    }
}