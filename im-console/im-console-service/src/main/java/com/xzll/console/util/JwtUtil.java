package com.xzll.console.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成和解析JWT Token
 *
 * @author xzll
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT密钥（建议从配置文件读取）
     */
    @Value("${jwt.secret:xzll-im-jwt-secret-key-2024-admin-console-authentication}")
    private String secret;

    /**
     * Token过期时间（默认7天）
     */
    @Value("${jwt.expiration:604800000}")
    private Long expiration;

    /**
     * 生成JWT Token
     *
     * @param adminId 管理员ID
     * @param username 用户名
     * @param roleCode 角色编码
     * @return JWT Token
     */
    public String generateToken(String adminId, String username, String roleCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("adminId", adminId);
        claims.put("username", username);
        claims.put("roleCode", roleCode);
        return generateToken(claims);
    }

    /**
     * 生成JWT Token
     *
     * @param claims 自定义声明
     * @return JWT Token
     */
    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从Token中获取Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("解析JWT Token失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中获取管理员ID
     *
     * @param token JWT Token
     * @return 管理员ID
     */
    public String getAdminIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("adminId", String.class) : null;
    }

    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    /**
     * 从Token中获取角色编码
     *
     * @param token JWT Token
     * @return 角色编码
     */
    public String getRoleCodeFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("roleCode", String.class) : null;
    }

    /**
     * 验证Token是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            log.error("验证Token失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断Token是否过期
     *
     * @param claims JWT Claims
     * @return 是否过期
     */
    public boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 刷新Token
     *
     * @param token 旧的Token
     * @return 新的Token
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("adminId", claims.get("adminId", String.class));
        newClaims.put("username", claims.get("username", String.class));
        newClaims.put("roleCode", claims.get("roleCode", String.class));
        return generateToken(newClaims);
    }

    /**
     * 获取签名密钥
     *
     * @return 签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 获取Token过期时间
     *
     * @param token JWT Token
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }
}
