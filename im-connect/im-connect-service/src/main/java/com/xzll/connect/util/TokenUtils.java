package com.xzll.connect.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xzll.common.constant.enums.ImTerminalType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Token解析工具类（轻量级版本）
 * 用于解析JWT Token中的用户信息和设备类型
 * 使用Base64解析，无需额外依赖
 */
@Slf4j
public class TokenUtils {

    /**
     * JWT中用户ID的key
     */
    private static final String JWT_USER_ID_KEY = "id";
    
    /**
     * JWT中设备类型的key
     */
    private static final String JWT_DEVICE_TYPE_KEY = "device_type";

    /**
     * 从JWT Token中解析用户ID（轻量级版本）
     * 
     * @param token JWT Token
     * @return 用户ID，解析失败返回null
     */
    public static String parseUserIdFromToken(String token) {
        try {
            JSONObject payload = parseJwtPayload(token);
            if (payload != null) {
                Object userIdObj = payload.get(JWT_USER_ID_KEY);
                if (userIdObj != null) {
                    return userIdObj.toString();
                }
            }
        } catch (Exception e) {
            log.error("解析Token中的用户ID失败：{}", token, e);
        }
        
        return null;
    }

    /**
     * 从JWT Token中解析设备类型（轻量级版本）
     * 
     * @param token JWT Token
     * @return 设备类型，解析失败返回null
     */
    public static ImTerminalType parseDeviceTypeFromToken(String token) {
        try {
            JSONObject payload = parseJwtPayload(token);
            if (payload != null) {
                Object deviceTypeObj = payload.get(JWT_DEVICE_TYPE_KEY);
                if (deviceTypeObj != null) {
                    Integer deviceTypeCode = Integer.valueOf(deviceTypeObj.toString());
                    return ImTerminalType.fromCode(deviceTypeCode);
                }
            }
        } catch (Exception e) {
            log.error("解析Token中的设备类型失败：{}", token, e);
        }
        
        return null;
    }

    /**
     * 解析JWT的Payload部分（轻量级版本）
     * JWT格式：header.payload.signature
     * 
     * @param token JWT Token
     * @return Payload的JSON对象，解析失败返回null
     */
    private static JSONObject parseJwtPayload(String token) {
        try {
            if (StringUtils.isBlank(token)) {
                return null;
            }
            
            // 移除 Bearer 前缀（如果有）
            String realToken = token.replace("Bearer ", "").trim();
            
            // JWT应该包含两个点，分成三部分：header.payload.signature
            String[] parts = realToken.split("\\.");
            if (parts.length != 3) {
                log.warn("Token格式不正确，不是有效的JWT格式：{}", token);
                return null;
            }
            
            // 第二部分是payload（索引1）
            String payloadBase64 = parts[1];
            
            // Base64解码payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            
            // 解析JSON
            return JSON.parseObject(payloadJson);
            
        } catch (Exception e) {
            log.error("解析JWT Payload失败：{}", token, e);
            return null;
        }
    }

    /**
     * 计算Token的MD5哈希值
     * 
     * @param token Token字符串
     * @return MD5哈希值，计算失败返回null
     */
    public static String calculateTokenMd5(String token) {
        try {
            if (StringUtils.isBlank(token)) {
                return null;
            }
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            
            // 将byte数组转换为16进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.error("计算Token MD5失败：{}", token, e);
            return null;
        }
    }

    /**
     * 验证Token格式是否为JWT（轻量级版本）
     * 
     * @param token Token字符串
     * @return true-是JWT格式，false-不是
     */
    public static boolean isValidJwtFormat(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        
        try {
            // 移除 Bearer 前缀（如果有）
            String realToken = token.replace("Bearer ", "").trim();
            
            // JWT应该包含两个点，分成三部分
            String[] parts = realToken.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            
            // 尝试解码第二部分（payload）来验证格式
            Base64.getUrlDecoder().decode(parts[1]);
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析Token的完整信息（轻量级版本）
     * 
     * @param token JWT Token
     * @return TokenInfo对象，包含用户ID、设备类型等信息
     */
    public static TokenInfo parseTokenInfo(String token) {
        try {
            String userId = parseUserIdFromToken(token);
            ImTerminalType deviceType = parseDeviceTypeFromToken(token);
            String tokenMd5 = calculateTokenMd5(token);
            
            if (StringUtils.isNotBlank(userId) && deviceType != null && StringUtils.isNotBlank(tokenMd5)) {
                return new TokenInfo(userId, deviceType, tokenMd5, token);
            }
            
        } catch (Exception e) {
            log.error("解析Token完整信息失败：{}", token, e);
        }
        
        return null;
    }

    public static void main(String[] args) {
        TokenInfo tokenInfo = TokenUtils.parseTokenInfo("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiIxMjM0Iiwic2NvcGUiOlsiYWxsIl0sImlkIjoxOTY5NjEwNjUwMjk0NTU0NjI0LCJleHAiOjE3NTg0NDU1NDksImF1dGhvcml0aWVzIjpbIkFETUlOIl0sImp0aSI6IjE3YWVjMDRkLTE0MTktNDgzYy04ZmZkLTJiYTUxMTk1MTA4MSIsImNsaWVudF9pZCI6ImNsaWVudC1hcHAifQ.XFnTyday2UNbtbKlh5Efz1F3UJ2pmnSkcZPtUwHpnGV4911bVFF0upb83HQu2yig7rN-B9FlgEjdgE-SXOsAeEFFSoNlFs51eGGfrqIxbChkX_qxDMSIwwgNmDDAxr9S0PFJw0IwvpgDI4Qc2vHRa5vXwbfqOXRwe0aT5HMfEJH9UAfY2aTbtVceeYIeQE3L_OXsRsixXX4YUH-GWOLOp094vPCEy0KDnlWm3ulqqLJDQzFF3tx8PdOOsPFpp8K_QVMGGrQpFcgQxGMuVElvs28qPhihCeE9gEySelY80JYkvrghKom_LLFCtVG_vBiH4uWTzoByoKkRLWrq0d395g");
        System.out.println(tokenInfo);


    }

    /**
     * Token信息封装类
     */
    public static class TokenInfo {
        private final String userId;
        private final ImTerminalType deviceType;
        private final String tokenMd5;
        private final String originalToken;

        public TokenInfo(String userId, ImTerminalType deviceType, String tokenMd5, String originalToken) {
            this.userId = userId;
            this.deviceType = deviceType;
            this.tokenMd5 = tokenMd5;
            this.originalToken = originalToken;
        }

        public String getUserId() {
            return userId;
        }

        public ImTerminalType getDeviceType() {
            return deviceType;
        }

        public String getTokenMd5() {
            return tokenMd5;
        }

        public String getOriginalToken() {
            return originalToken;
        }

        /**
         * 构建Redis Key
         * 格式：USER_TOKEN_KEY + userId + ":" + deviceType.getCode() + ":" + tokenMd5
         */
        public String buildRedisKey(String userTokenKeyPrefix) {
            return userTokenKeyPrefix + userId + ":" + deviceType.getCode() + ":" + tokenMd5;
        }

        @Override
        public String toString() {
            return "TokenInfo{" +
                    "userId='" + userId + '\'' +
                    ", deviceType=" + deviceType +
                    ", tokenMd5='" + tokenMd5 + '\'' +
                    '}';
        }
    }
}