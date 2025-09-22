package com.xzll.gateway.authorization;

import cn.hutool.core.convert.Convert;
import cn.hutool.crypto.digest.DigestUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.enums.ImTerminalType;
import com.xzll.gateway.config.AuthConfig;
import com.xzll.gateway.constant.AuthConstant;
import com.xzll.common.constant.RedisConstant;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.xzll.common.utils.RedissonUtils;
import org.redisson.api.RedissonClient;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:52:49
 * @Description: 认证管理器(重要)
 * <p>
 * 鉴权流程：
 * 1. 验证JWT的有效性和权限
 * 2. 检查Redis中token是否存在（防止已登出的token继续使用）
 * 3. 验证用户是否有访问当前路径的权限
 */
@Slf4j
@Component
public class AuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    @Autowired
    private RedissonUtils redissonUtils;
    
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private AuthConfig authConfig;

    @SneakyThrows
    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> mono, AuthorizationContext authorizationContext) {
        // 获取请求路径
        URI uri = authorizationContext.getExchange().getRequest().getURI();
        String requestPath = uri.getPath();
        String requestMethod = authorizationContext.getExchange().getRequest().getMethod().name();
        
        log.debug("权限校验开始 - 路径: {}, 方法: {}", requestPath, requestMethod);
        
        // 检查是否需要绕过接口权限验证（但保留Token认证）
        boolean bypassPermissionCheck = authConfig.shouldBypassPermissionCheck(requestPath);
        if (bypassPermissionCheck) {
            log.info("绕过接口权限验证，但保留Token认证 - 路径: {}", requestPath);
        }
        
        // 从Redis中获取当前路径可访问角色列表
        Object obj = redissonUtils.getHash(RedisConstant.RESOURCE_ROLES_MAP, requestPath);
        List<String> authorities = Convert.toList(String.class, obj);
        
        // 如果没有配置权限且未绕过权限检查，记录警告并拒绝访问
        if (authorities == null || authorities.isEmpty()) {
            if (!bypassPermissionCheck) {
                log.warn("接口未配置权限规则，拒绝访问 - 路径: {}", requestPath);
                return Mono.just(new AuthorizationDecision(false));
            } else {
                log.info("接口未配置权限规则，但已绕过权限检查 - 路径: {}", requestPath);
            }
        }
        
        // 添加权限前缀
        final List<String> finalAuthorities = authorities != null ? authorities.stream()
                .map(i -> i = AuthConstant.AUTHORITY_PREFIX + i)
                .collect(Collectors.toList()) : null;
        
        if (finalAuthorities != null) {
            log.info("接口所需权限: {} - 路径: {}", finalAuthorities, requestPath);
        }
        
        // 认证通过且角色匹配的用户可访问当前路径
        return mono
                .filter(Authentication::isAuthenticated)
                .filter(authentication -> {
                    // 检查Redis中token是否存在
                    boolean tokenValid = checkTokenInRedis(authentication, authorizationContext);
                    if (!tokenValid) {
                        log.warn("Token验证失败 - 路径: {}, 用户: {}", requestPath, 
                                authentication.getName());
                    }
                    return tokenValid;
                })
                .flatMap(authentication -> {
                    // 如果绕过权限检查，直接通过（但Token认证已完成）
                    if (bypassPermissionCheck) {
                        log.debug("Token认证通过，绕过接口权限检查 - 用户: {}, 路径: {}", 
                                authentication.getName(), requestPath);
                        return Mono.just(new AuthorizationDecision(true));
                    }
                    
                    // 获取用户权限列表
                    List<String> userAuthorities = authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList());
                    
                    log.debug("用户权限: {} - 用户: {}, 路径: {}", userAuthorities, 
                            authentication.getName(), requestPath);
                    
                    // 检查是否有匹配的权限
                    boolean hasPermission = userAuthorities.stream()
                            .anyMatch(finalAuthorities::contains);
                    
                    if (!hasPermission) {
                        log.warn("权限不足 - 用户: {}, 用户权限: {}, 所需权限: {}, 路径: {}", 
                                authentication.getName(), userAuthorities, finalAuthorities, requestPath);
                    } else {
                        log.debug("权限校验通过 - 用户: {}, 路径: {}", authentication.getName(), requestPath);
                    }
                    
                    return Mono.just(new AuthorizationDecision(hasPermission));
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    /**
     * 检查Redis中token是否存在
     * <p>
     * 检查逻辑：
     * 1. 从JWT中提取用户ID
     * 2. 计算token的MD5哈希值
     * 3. 在Redis中查找所有匹配的token记录（支持多设备类型）
     * 4. 如果不存在，说明token已被登出
     *
     * @param authentication 认证对象
     * @param authorizationContext 授权上下文
     * @return true表示token存在，false表示token不存在
     */
    private boolean checkTokenInRedis(Authentication authentication, AuthorizationContext authorizationContext) {
        try {
            // 获取JWT对象
            if (!(authentication.getPrincipal() instanceof Jwt)) {
                log.warn("认证主体不是JWT类型");
                return false;
            }
            
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String token = jwt.getTokenValue();
            
            // 从JWT中获取用户ID
            Long idClaim = jwt.getClaim("id");
            if (idClaim == null) {
                log.warn("JWT中未找到用户ID");
                return false;
            }
            String userId = String.valueOf(idClaim);
            
            // 从JWT中获取设备类型
            Object deviceTypeClaim = jwt.getClaim("device_type");
            Integer deviceTypeCode = null;
            if (deviceTypeClaim != null) {
                // 安全地处理不同类型的设备类型
                if (deviceTypeClaim instanceof Integer) {
                    deviceTypeCode = (Integer) deviceTypeClaim;
                } else if (deviceTypeClaim instanceof Long) {
                    deviceTypeCode = ((Long) deviceTypeClaim).intValue();
                } else if (deviceTypeClaim instanceof String) {
                    try {
                        deviceTypeCode = Integer.valueOf((String) deviceTypeClaim);
                    } catch (NumberFormatException e) {
                        log.warn("设备类型格式错误: {}", deviceTypeClaim);
                    }
                }
            }
            if (deviceTypeCode == null) {
                log.warn("JWT中未找到有效的设备类型，使用兼容模式查找所有设备类型");
                // 兼容模式：查找所有设备类型
                return checkTokenInRedisCompatibleMode(jwt, userId);
            }

            
            ImTerminalType deviceType = ImTerminalType.fromCode(deviceTypeCode);
            if (deviceType == null || (deviceType != ImTerminalType.ANDROID && deviceType != ImTerminalType.IOS && 
                    deviceType != ImTerminalType.MINI_PROGRAM && deviceType != ImTerminalType.WEB)) {
                log.warn("无效的设备类型: {}, 使用兼容模式查找所有设备类型", deviceTypeCode);
                return checkTokenInRedisCompatibleMode(jwt, userId);
            }
            
            // 计算token的MD5哈希值
            String tokenMd5 = DigestUtil.md5Hex(token);
            
            // 构建Redis key模式：USER_TOKEN_KEY + userId + deviceType + tokenMd5
            String keyPattern = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + userId + ":" + deviceType.getCode() + ":" + tokenMd5;
            
            // 使用RedissonClient查找匹配的keys
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(keyPattern);
            
            // 检查是否存在匹配的token记录
            boolean tokenExists = false;
            for (String key : keys) {
                String storedUserId = redissonUtils.getString(key);
                if (storedUserId != null && storedUserId.equals(userId)) {
                    tokenExists = true;
                    break;
                }
            }
            
            if (!tokenExists) {
                log.warn("Token在Redis中不存在，可能已被登出，用户ID: {}, tokenMd5: {}", userId, tokenMd5);
            }
            
            return tokenExists;
            
        } catch (Exception e) {
            log.error("检查Redis中token存在性时发生异常", e);
            return false;
        }
    }

    /**
     * 兼容模式：检查Redis中token是否存在（查找所有设备类型）
     * <p>
     * 用于处理JWT中没有设备类型信息的情况
     *
     * @param jwt JWT对象
     * @param userId 用户ID
     * @return true表示token存在，false表示token不存在
     */
    private boolean checkTokenInRedisCompatibleMode(Jwt jwt, String userId) {
        try {
            String token = jwt.getTokenValue();
            String tokenMd5 = DigestUtil.md5Hex(token);
            
            // 确保userId不为空
            if (userId == null || userId.isEmpty()) {
                log.warn("兼容模式：用户ID为空");
                return false;
            }
            
            // 查找该用户所有设备类型的token
            String keyPattern = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + userId + ":*:" + tokenMd5;
            
            // 使用RedissonClient查找匹配的keys
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(keyPattern);
            
            // 检查是否存在匹配的token记录
            boolean tokenExists = false;
            for (String key : keys) {
                String storedUserId = redissonUtils.getString(key);
                if (storedUserId != null && storedUserId.equals(userId)) {
                    tokenExists = true;
                    break;
                }
            }
            
            if (!tokenExists) {
                log.warn("兼容模式：Token在Redis中不存在，可能已被登出，用户ID: {}, tokenMd5: {}", userId, tokenMd5);
            }
            
            return tokenExists;
            
        } catch (Exception e) {
            log.error("兼容模式检查Redis中token存在性时发生异常", e);
            return false;
        }
    }
}
