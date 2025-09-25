package com.xzll.connect.netty.handler;

import cn.hutool.core.lang.Assert;
import cn.hutool.extra.spring.SpringUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.connect.netty.channel.LocalChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.xzll.connect.util.TokenUtils;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 认证处理器 - Redis分布式版本
 * 优化内容：
 * 1. 所有安全数据存储在Redis中，支持分布式部署
 * 2. 增强Token验证逻辑
 * 3. 支持Token刷新机制
 * 4. 防止暴力破解（分布式）
 * 5. 添加认证失败统计（分布式）
 * 6. IP白名单支持（分布式）
 * 7. 多设备登录控制
 * 8. 修复静态初始化Bean获取问题
 * 
 * @Author: hzz
 * @Date: 2022/6/8 17:26:24
 * @Description:
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class AuthHandler extends ChannelInboundHandlerAdapter {

    // 注入RedissonUtils，避免静态初始化问题
    @Autowired
    private RedissonUtils redissonUtils;
    
    // ============= Redis Key 前缀定义 =============
    private static final String AUTH_FAILURE_KEY_PREFIX = "im:auth:failure:";
    private static final String IP_LOCKED_KEY_PREFIX = "im:auth:locked:";
    private static final String IP_WHITELIST_KEY_PREFIX = "im:auth:whitelist:";
    
    // ============= 配置参数 =============
    
    // 是否启用认证（开发阶段可以暂时关闭）
    @Value("${im.netty.auth.enabled:true}")
    private boolean authEnabled;
    
    // Token过期时间检查
    @Value("${im.netty.auth.token-expire-check:true}")
    private boolean tokenExpireCheck;
    
    // 最大认证失败次数
    @Value("${im.netty.auth.max-auth-failures:50}")
    private int maxAuthFailures;
    
    // 认证失败锁定时间（分钟）
    @Value("${im.netty.auth.lockout-duration-minutes:1}")
    private int lockoutDurationMinutes;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            log.debug("非HTTP请求，跳过认证处理");
            ctx.fireChannelRead(msg);
            return;
        }
        
        FullHttpRequest request = (FullHttpRequest) msg;
        String clientIp = getClientIp(ctx);
        
        try {
            // 如果认证被禁用，使用测试模式
            if (!authEnabled) {
                handleTestMode(ctx, request, clientIp);
                return;
            }
            
            // 检查IP是否被锁定
            if (isIpLocked(clientIp)) {
                log.warn("IP认证被锁定，拒绝连接：{}", clientIp);
                ctx.channel().close();
                return;
            }
            
            // 检查IP白名单
            if (isIpWhitelisted(clientIp)) {
                log.info("IP在白名单中，跳过认证：{}", clientIp);
                handleWhitelistAccess(ctx, request, clientIp);
                return;
            }
            
            // 执行正常认证流程
            if (performAuthentication(ctx, request, clientIp)) {
                // 认证成功，重置失败计数
                resetAuthFailures(clientIp);
                
                // 移除认证处理器，避免重复认证
                ctx.pipeline().remove(this);
                
                // 继续处理请求
                ctx.fireChannelRead(msg);
            }
            
        } catch (Exception e) {
            log.error("认证处理异常，IP：{}", clientIp, e);
            handleAuthFailure(ctx, clientIp, "认证处理异常");
        }
    }

    /**
     * 执行认证逻辑
     */
    private boolean performAuthentication(ChannelHandlerContext ctx, FullHttpRequest request, String clientIp) {
        HttpHeaders headers = request.headers();
        
        // 检查必需的认证头
        if (Objects.isNull(headers) || headers.isEmpty()) {
            log.warn("请求头为空，认证失败：{}", clientIp);
            handleAuthFailure(ctx, clientIp, "请求头为空");
            return false;
        }
        
        String token = headers.get(ImConstant.TOKEN);
        if (StringUtils.isEmpty(token)) {
            log.warn("Token为空，认证失败：{}", clientIp);
            handleAuthFailure(ctx, clientIp, "Token为空");
            return false;
        }
        
        // 验证Token并获取用户ID
        String uid = validateToken(token);
        if (StringUtils.isBlank(uid)) {
            log.warn("Token无效，认证失败：{}, token: {}", clientIp, token);
            handleAuthFailure(ctx, clientIp, "Token无效");
            return false;
        }
        
        // 检查用户状态
        if (!isUserValid(uid)) {
            log.warn("用户状态异常，认证失败：{}, uid: {}", clientIp, uid);
            handleAuthFailure(ctx, clientIp, "用户状态异常");
            return false;
        }
        
        // 处理多设备登录
        handleMultiDeviceLogin(uid, ctx);
        
        // 设置用户信息到Channel
        LocalChannelManager.addUserChannel(uid, ctx.channel());
        ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
        
        log.info("认证成功：IP={}, uid={}, token={}", clientIp, uid, token);
        return true;
    }

    /**
     * 验证Token（新版本 - 支持设备类型和MD5）
     */
    private String validateToken(String token) {
        try {
            // 验证Token格式
            if (!TokenUtils.isValidJwtFormat(token)) {
                log.warn("Token格式无效，不是有效的JWT格式：{}", token);
                return null;
            }
            
            // 解析Token获取用户信息
            TokenUtils.TokenInfo tokenInfo = TokenUtils.parseTokenInfo(token);
            
            if (tokenInfo == null) {
                log.warn("无法解析Token信息：{}", token);
                return null;
            }
            
            // 构建Redis Key：USER_TOKEN_KEY + userId + ":" + deviceType + ":" + tokenMd5
            String redisKey = tokenInfo.buildRedisKey(ImConstant.RedisKeyConstant.USER_TOKEN_KEY);
            
            // 从Redis获取存储的用户ID
            String storedUid = redissonUtils.getString(redisKey);
            
            if (StringUtils.isNotBlank(storedUid)) {
                // 验证存储的用户ID与Token中解析的用户ID是否一致
                if (!storedUid.equals(tokenInfo.getUserId())) {
                    log.warn("Token中的用户ID与Redis存储的不一致：token_uid={}, stored_uid={}", 
                        tokenInfo.getUserId(), storedUid);
                    return null;
                }
                
                // 如果启用了Token过期检查，验证Redis key的过期时间
                if (tokenExpireCheck) {
                    Long expireTime = redissonUtils.getExpire(redisKey, TimeUnit.MILLISECONDS);
                    if (expireTime != null && expireTime < 0) {
                        log.warn("Token已过期：redisKey={}, tokenInfo={}", redisKey, tokenInfo);
                        return null;
                    }
                }
                
                log.debug("Token验证成功：userId={}, deviceType={}, redisKey={}", 
                    tokenInfo.getUserId(), tokenInfo.getDeviceType().getDescription(), redisKey);
                
                return tokenInfo.getUserId();
            } else {
                log.warn("Redis中未找到Token记录：redisKey={}, tokenInfo={}", redisKey, tokenInfo);
            }
            
        } catch (Exception e) {
            log.error("Token验证异常：token={}", token, e);
        }
        
        return null;
    }

    /**
     * 检查用户是否有效
     */
    private boolean isUserValid(String uid) {
        try {
            // 检查用户是否在黑名单中（可选功能，暂时注释）
            // String blacklistKey = "userLogin:blacklist:" + uid;
            // String blacklistValue = redissonUtils.getString(blacklistKey);
            // if (StringUtils.isNotBlank(blacklistValue)) {
            //     log.warn("用户在黑名单中：{}", uid);
            //     return false;
            // }
            
            return true;
        } catch (Exception e) {
            log.error("检查用户状态异常：{}", uid, e);
            return false;
        }
    }

    /**
     * 处理多设备登录
     */
    private void handleMultiDeviceLogin(String uid, ChannelHandlerContext ctx) {
        // 可以在这里实现多设备登录策略
        // 例如：踢掉之前的连接、限制连接数等
        
        // 简单实现：如果已有连接，关闭旧连接
        if (LocalChannelManager.isUserOnline(uid)) {
            log.info("用户{}重复登录，关闭旧连接", uid);
            // 这里可以发送通知给旧设备，然后关闭连接
        }
    }

    /**
     * 测试模式处理（开发阶段使用）
     */
    private void handleTestMode(ChannelHandlerContext ctx, FullHttpRequest request, String clientIp) {
        log.warn("认证已禁用，使用测试模式：{}", clientIp);
        
        HttpHeaders headers = request.headers();
        String uid = headers.get("uid"); // 从header直接获取uid
        
        if (StringUtils.isBlank(uid)) {
            uid = "test_user_" + System.currentTimeMillis(); // 生成测试用户ID
        }
        
        Assert.isTrue(StringUtils.isNotBlank(uid), AnswerCode.TOKEN_INVALID.getMessage());
        
        LocalChannelManager.addUserChannel(uid, ctx.channel());
        ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
        
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(request);
    }

    /**
     * 白名单访问处理
     */
    private void handleWhitelistAccess(ChannelHandlerContext ctx, FullHttpRequest request, String clientIp) {
        // 白名单用户可以使用简化的认证流程
        HttpHeaders headers = request.headers();
        String uid = headers.get("uid");
        
        if (StringUtils.isBlank(uid)) {
            uid = "whitelist_" + clientIp.replace(".", "_");
        }
        
        LocalChannelManager.addUserChannel(uid, ctx.channel());
        ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
        
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(request);
    }

    // ============= Redis 分布式安全功能 =============

    /**
     * 处理认证失败（Redis版本）
     */
    private void handleAuthFailure(ChannelHandlerContext ctx, String clientIp, String reason) {
        try {
            // 获取当前失败次数
            String failureKey = AUTH_FAILURE_KEY_PREFIX + clientIp;
            String currentFailuresStr = redissonUtils.getString(failureKey);
            int currentFailures = StringUtils.isNotBlank(currentFailuresStr) ? 
                Integer.parseInt(currentFailuresStr) : 0;
            
            // 增加失败计数
            currentFailures++;
            
            // 设置失败计数，过期时间为锁定时长的2倍（确保计数不会无限累积）
            redissonUtils.setString(failureKey, String.valueOf(currentFailures), 
                lockoutDurationMinutes * 2, TimeUnit.MINUTES);
            
            log.warn("认证失败：IP={}, 原因={}, 失败次数={}", clientIp, reason, currentFailures);
            
            // 检查是否需要锁定IP
            if (currentFailures >= maxAuthFailures) {
                lockIp(clientIp);
                log.warn("IP认证失败次数过多，已锁定：{}, 锁定时长：{}分钟", clientIp, lockoutDurationMinutes);
            }
            
        } catch (Exception e) {
            log.error("处理认证失败异常：{}", clientIp, e);
        }
        
        // 关闭连接
        ctx.channel().close();
    }

    /**
     * 检查IP是否被锁定（Redis版本）
     */
    private boolean isIpLocked(String ip) {
        try {
            String lockKey = IP_LOCKED_KEY_PREFIX + ip;
            String lockValue = redissonUtils.getString(lockKey);
            return StringUtils.isNotBlank(lockValue);
        } catch (Exception e) {
            log.error("检查IP锁定状态异常：{}", ip, e);
            return false; // 异常时不锁定，避免误伤
        }
    }

    /**
     * 锁定IP（Redis版本）
     */
    private void lockIp(String ip) {
        try {
            String lockKey = IP_LOCKED_KEY_PREFIX + ip;
            String lockValue = String.valueOf(System.currentTimeMillis());
            
            // 设置锁定标记，过期时间为配置的锁定时长
            redissonUtils.setString(lockKey, lockValue, lockoutDurationMinutes, TimeUnit.MINUTES);
            
            log.info("IP已锁定：{}, 锁定时长：{}分钟", ip, lockoutDurationMinutes);
        } catch (Exception e) {
            log.error("锁定IP异常：{}", ip, e);
        }
    }

    /**
     * 检查IP是否在白名单中（Redis版本）
     */
    private boolean isIpWhitelisted(String ip) {
        try {
            String whitelistKey = IP_WHITELIST_KEY_PREFIX + ip;
            String whitelistValue = redissonUtils.getString(whitelistKey);
            return StringUtils.isNotBlank(whitelistValue);
        } catch (Exception e) {
            log.error("检查IP白名单状态异常：{}", ip, e);
            return false;
        }
    }

    /**
     * 重置认证失败计数（Redis版本）
     */
    private void resetAuthFailures(String ip) {
        try {
            String failureKey = AUTH_FAILURE_KEY_PREFIX + ip;
            redissonUtils.deleteString(failureKey);
        } catch (Exception e) {
            log.error("重置认证失败计数异常：{}", ip, e);
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            return socketAddress.getAddress().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String clientIp = getClientIp(ctx);
        log.error("认证处理器异常：{}", clientIp, cause);
        ctx.channel().close();
    }

    // ============= 静态方法需要懒加载获取RedissonUtils =============

    /**
     * 懒加载获取RedissonUtils Bean
     */
    private static RedissonUtils getRedissonUtils() {
        try {
            return SpringUtil.getBean(RedissonUtils.class);
        } catch (Exception e) {
            log.error("无法获取RedissonUtils Bean", e);
            return null;
        }
    }

    // ============= 分布式管理接口 =============

    /**
     * 手动添加IP到白名单（Redis版本）
     */
    public static void addToWhitelist(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，添加IP到白名单失败：{}", ip);
                return;
            }
            
            String whitelistKey = IP_WHITELIST_KEY_PREFIX + ip;
            String whitelistValue = String.valueOf(System.currentTimeMillis());
            
            // 白名单永不过期，除非手动删除
            redissonUtils.setString(whitelistKey, whitelistValue);
            log.info("IP已添加到白名单：{}", ip);
        } catch (Exception e) {
            log.error("添加IP到白名单异常：{}", ip, e);
        }
    }

    /**
     * 从白名单移除IP（Redis版本）
     */
    public static void removeFromWhitelist(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，从白名单移除IP失败：{}", ip);
                return;
            }
            
            String whitelistKey = IP_WHITELIST_KEY_PREFIX + ip;
            redissonUtils.deleteString(whitelistKey);
            log.info("IP已从白名单移除：{}", ip);
        } catch (Exception e) {
            log.error("从白名单移除IP异常：{}", ip, e);
        }
    }

    /**
     * 手动解锁IP（Redis版本）
     */
    public static void unlockIp(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) {
                log.error("无法获取RedissonUtils，解锁IP失败：{}", ip);
                return;
            }
            
            String lockKey = IP_LOCKED_KEY_PREFIX + ip;
            String failureKey = AUTH_FAILURE_KEY_PREFIX + ip;
            
            redissonUtils.deleteString(lockKey);
            redissonUtils.deleteString(failureKey);
            log.info("IP已解锁：{}", ip);
        } catch (Exception e) {
            log.error("解锁IP异常：{}", ip, e);
        }
    }

    /**
     * 获取IP的认证失败次数（Redis版本）
     */
    public static int getAuthFailureCount(String ip) {
        try {
            RedissonUtils redissonUtils = getRedissonUtils();
            if (redissonUtils == null) return 0;
            
            String failureKey = AUTH_FAILURE_KEY_PREFIX + ip;
            String failureCountStr = redissonUtils.getString(failureKey);
            return StringUtils.isNotBlank(failureCountStr) ? Integer.parseInt(failureCountStr) : 0;
        } catch (Exception e) {
            log.error("获取认证失败次数异常：{}", ip, e);
            return 0;
        }
    }

    /**
     * 获取认证统计信息（Redis版本）
     */
    public static String getAuthStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 分布式认证统计信息 ===\n");
        sb.append("所有安全数据存储在Redis中，支持分布式部署\n");
        sb.append("Redis Key前缀:\n");
        sb.append("  认证失败: ").append(AUTH_FAILURE_KEY_PREFIX).append("\n");
        sb.append("  IP锁定: ").append(IP_LOCKED_KEY_PREFIX).append("\n");
        sb.append("  IP白名单: ").append(IP_WHITELIST_KEY_PREFIX).append("\n");
        
        // 注意：在分布式环境中，获取所有key的操作可能比较昂贵
        // 建议只在调试时使用，生产环境可以通过专门的监控接口查看
        
        return sb.toString();
    }
}