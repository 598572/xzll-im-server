package com.xzll.auth.service;

import com.xzll.auth.config.Oauth2Config;
import com.xzll.auth.constant.AuthConstant;
import com.xzll.auth.domain.SecurityUser;
import com.xzll.auth.util.DeviceTypeContext;
import com.xzll.common.constant.enums.ImTerminalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:06:10
 * @Description: JWT Token 服务（Spring Boot 3.x + Spring Security 6.x）
 * 替代原来的 JwtTokenEnhancer、TokenStore 和 JwtAccessTokenConverter
 * 
 * 功能：
 * 1. 生成 JWT 访问令牌（包含用户ID和设备类型）
 * 2. 生成刷新令牌
 * 3. 验证和解析 JWT Token
 * 4. 从 Token 中提取用户信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Oauth2Config oauth2Config;

    /**
     * 为用户生成访问令牌
     *
     * @param securityUser 安全用户对象
     * @param deviceType   设备类型
     * @return JWT Token字符串
     */
    public String generateAccessToken(SecurityUser securityUser, ImTerminalType deviceType) {
        if (securityUser == null || securityUser.getId() == null) {
            throw new RuntimeException("用户信息不能为空");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(oauth2Config.getTokenTimeOut(), ChronoUnit.SECONDS);

        // 构建JWT Claims
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("im-auth")
                .subject(securityUser.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AuthConstant.JWT_USER_ID_KEY, securityUser.getId());

        // 添加用户权限/角色到JWT中（重要：Gateway需要从此字段提取权限）
        if (securityUser.getAuthorities() != null && !securityUser.getAuthorities().isEmpty()) {
            // 将权限列表转换为字符串数组
            String[] authorities = securityUser.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toArray(String[]::new);
            claimsBuilder.claim("authorities", authorities);
            log.info("将用户权限添加到JWT中: userId={}, authorities={}", securityUser.getId(), authorities);
        } else {
            // 如果没有权限，添加默认角色（ROLE_ 前缀是Gateway的权限前缀）
            String[] defaultAuthorities = new String[]{"ROLE_ADMIN"};
            claimsBuilder.claim("authorities", defaultAuthorities);
            log.warn("用户没有配置权限，使用默认角色: userId={}, authorities={}", securityUser.getId(), defaultAuthorities);
        }

        // 添加设备类型到JWT中
        if (deviceType != null && deviceType != ImTerminalType.UNKNOWN) {
            claimsBuilder.claim(AuthConstant.JWT_DEVICE_TYPE_KEY, deviceType.getCode());
            log.info("将设备类型添加到JWT中: userId={}, deviceType={}", securityUser.getId(), deviceType.getDescription());
        } else {
            // 尝试从ThreadLocal中获取设备类型（刷新token时使用）
            ImTerminalType deviceTypeFromContext = DeviceTypeContext.getDeviceType();
            if (deviceTypeFromContext != null && deviceTypeFromContext != ImTerminalType.UNKNOWN) {
                claimsBuilder.claim(AuthConstant.JWT_DEVICE_TYPE_KEY, deviceTypeFromContext.getCode());
                log.info("从ThreadLocal中获取到设备类型并添加到JWT: userId={}, deviceType={}",
                        securityUser.getId(), deviceTypeFromContext.getDescription());
            } else {
                log.warn("未找到设备类型信息，userId={}", securityUser.getId());
            }
        }

        JwtClaimsSet claims = claimsBuilder.build();

        // 编码JWT
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims));

        log.debug("生成访问令牌成功，userId={}, expiresAt={}", securityUser.getId(), expiresAt);
        return jwt.getTokenValue();
    }

    /**
     * 生成刷新令牌
     *
     * @param securityUser 安全用户对象
     * @param deviceType   设备类型
     * @return 刷新令牌字符串
     */
    public String generateRefreshToken(SecurityUser securityUser, ImTerminalType deviceType) {
        if (securityUser == null || securityUser.getId() == null) {
            throw new RuntimeException("用户信息不能为空");
        }

        Instant now = Instant.now();
        // 刷新令牌有效期通常比访问令牌长
        int refreshTokenTimeout = oauth2Config.getRefreshToken() != null ? oauth2Config.getRefreshToken() : 86400;
        Instant expiresAt = now.plus(refreshTokenTimeout, ChronoUnit.SECONDS);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("im-auth")
                .subject(securityUser.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AuthConstant.JWT_USER_ID_KEY, securityUser.getId())
                .claim("token_type", "refresh");

        // 添加设备类型到刷新令牌
        if (deviceType != null && deviceType != ImTerminalType.UNKNOWN) {
            claimsBuilder.claim(AuthConstant.JWT_DEVICE_TYPE_KEY, deviceType.getCode());
        }

        JwtClaimsSet claims = claimsBuilder.build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims));

        log.debug("生成刷新令牌成功，userId={}, expiresAt={}", securityUser.getId(), expiresAt);
        return jwt.getTokenValue();
    }

    /**
     * 验证并解析JWT Token
     *
     * @param token JWT Token字符串
     * @return 解析后的JWT对象，如果无效则返回null
     */
    public Jwt validateAndParseToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            
            // 检查是否过期
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                log.warn("Token已过期，expiresAt={}", expiresAt);
                return null;
            }
            
            return jwt;
        } catch (JwtException e) {
            log.warn("Token验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     *
     * @param token JWT Token字符串
     * @return 用户ID，如果获取失败返回null
     */
    public Long getUserIdFromToken(String token) {
        try {
            Jwt jwt = validateAndParseToken(token);
            if (jwt == null) {
                return null;
            }
            Object userId = jwt.getClaim(AuthConstant.JWT_USER_ID_KEY);
            if (userId != null) {
                return Long.valueOf(userId.toString());
            }
            return null;
        } catch (Exception e) {
            log.error("从Token中获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取设备类型
     *
     * @param token JWT Token字符串
     * @return 设备类型，如果获取失败返回null
     */
    public ImTerminalType getDeviceTypeFromToken(String token) {
        try {
            Jwt jwt = validateAndParseToken(token);
            if (jwt == null) {
                return null;
            }
            Object deviceTypeCode = jwt.getClaim(AuthConstant.JWT_DEVICE_TYPE_KEY);
            if (deviceTypeCode != null) {
                return ImTerminalType.fromCode(Integer.valueOf(deviceTypeCode.toString()));
            }
            return null;
        } catch (Exception e) {
            log.error("从Token中获取设备类型失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取用户名（subject）
     *
     * @param token JWT Token字符串
     * @return 用户名，如果获取失败返回null
     */
    public String getUsernameFromToken(String token) {
        try {
            Jwt jwt = validateAndParseToken(token);
            if (jwt == null) {
                return null;
            }
            return jwt.getSubject();
        } catch (Exception e) {
            log.error("从Token中获取用户名失败", e);
            return null;
        }
    }

    /**
     * 判断Token是否为刷新令牌
     *
     * @param token JWT Token字符串
     * @return true表示是刷新令牌
     */
    public boolean isRefreshToken(String token) {
        try {
            Jwt jwt = validateAndParseToken(token);
            if (jwt == null) {
                return false;
            }
            Object tokenType = jwt.getClaim("token_type");
            return "refresh".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取Token的附加信息（用于兼容旧代码）
     *
     * @param token JWT Token字符串
     * @return 附加信息Map
     */
    public Map<String, Object> getAdditionalInformation(String token) {
        Map<String, Object> additionalInfo = new HashMap<>();
        try {
            Jwt jwt = validateAndParseToken(token);
            if (jwt == null) {
                return additionalInfo;
            }
            
            // 提取用户ID
            Object userId = jwt.getClaim(AuthConstant.JWT_USER_ID_KEY);
            if (userId != null) {
                additionalInfo.put(AuthConstant.JWT_USER_ID_KEY, userId);
            }
            
            // 提取设备类型
            Object deviceType = jwt.getClaim(AuthConstant.JWT_DEVICE_TYPE_KEY);
            if (deviceType != null) {
                additionalInfo.put(AuthConstant.JWT_DEVICE_TYPE_KEY, deviceType);
            }
            
        } catch (Exception e) {
            log.error("获取Token附加信息失败", e);
        }
        return additionalInfo;
    }
}
