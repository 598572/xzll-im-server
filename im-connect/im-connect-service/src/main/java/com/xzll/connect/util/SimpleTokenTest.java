package com.xzll.connect.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 简化的Token测试类
 */
public class SimpleTokenTest {
    
    public static void main(String[] args) {
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJ4emxsYWRnMDIiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxOTY1Njg3MDczOTEyNTI0ODAwLCJleHAiOjE3NTc1MTIxMTEsImF1dGhvcml0aWVzIjpbIkFETUlOIl0sImp0aSI6IjlmYmZlNDFhLTI5MzktNDI2Ny1iODUwLTU2NDM5ZGJjNjliMyIsImNsaWVudF9pZCI6ImNsaWVudC1hcHAifQ.zR9UE9x0CnX_yD4RqNB8bWjFzcCwlFOiiEM1tiJ23kr7CPA6UOLya-xyrVpGKxbSJf7HTOPLZwQNjR3yOyr3T3SII9qd-ja6-Qisv6U8cBcgezQ4NwILxBc165mGol0rf5SgLy9TNUOXXp8uT_su1ZI8VvpyA8d7ZeXP8bfy6gF9cULXA2sjhpB2Q5Uz7APFJKX7dbVY8MyFyWE6nkE-ER4o6zZnTJQ4oBnljb_ovhuh-Zennv8MuUhuiOVDdoVeEE4CPnW-rGcnkut4KY8MVxYF-3M0NQC5cjdH4JZ8CYvKThkH9_51_sR6geMq5oqCpvHIt6_wzKeQrduu5RyzIA";
        
        System.out.println("========== JWT Token 解析测试 ==========");
        System.out.println("原始Token: " + token);
        System.out.println();
        
        // 1. 解析JWT Payload
        JSONObject payload = parseJwtPayload(token);
        if (payload != null) {
            System.out.println("✅ JWT解析成功:");
            System.out.println("Payload JSON: " + payload.toJSONString());
            System.out.println();
            
            // 2. 提取关键字段
            System.out.println("关键字段:");
            Object id = payload.get("id");
            Object deviceType = payload.get("device_type");
            Object userName = payload.get("user_name");
            
            System.out.println("  id: " + id + " (类型: " + (id != null ? id.getClass().getSimpleName() : "null") + ")");
            System.out.println("  device_type: " + deviceType + " (类型: " + (deviceType != null ? deviceType.getClass().getSimpleName() : "null") + ")");
            System.out.println("  user_name: " + userName);
            System.out.println();
            
            // 3. 生成用户ID字符串
            String userId = null;
            if (id != null) {
                userId = id.toString();
                System.out.println("✅ 用户ID提取成功: " + userId);
            } else {
                System.out.println("❌ 用户ID提取失败");
            }
            
            // 4. 生成设备类型
            Integer deviceTypeCode = null;
            if (deviceType != null) {
                deviceTypeCode = Integer.valueOf(deviceType.toString());
                System.out.println("✅ 设备类型提取成功: " + deviceTypeCode);
                
                // 映射设备类型描述
                String deviceDesc = getDeviceTypeDescription(deviceTypeCode);
                System.out.println("  设备类型描述: " + deviceDesc);
            } else {
                System.out.println("❌ 设备类型提取失败");
            }
            
            // 5. 计算Token MD5
            String tokenMd5 = calculateMd5(token);
            System.out.println("✅ Token MD5: " + tokenMd5);
            System.out.println();
            
            // 6. 生成Redis Key
            if (userId != null && deviceTypeCode != null && tokenMd5 != null) {
                String redisKey = "userLogin:token:" + userId + ":" + deviceTypeCode + ":" + tokenMd5;
                System.out.println("✅ 生成Redis Key: " + redisKey);
                
                // 7. 模拟验证流程
                System.out.println();
                System.out.println("========== 模拟验证流程 ==========");
                System.out.println("1. 客户端发送Token");
                System.out.println("2. 解析Token获取: userId=" + userId + ", deviceType=" + deviceTypeCode);
                System.out.println("3. 计算Token MD5: " + tokenMd5);
                System.out.println("4. 构建Redis Key: " + redisKey);
                System.out.println("5. 查找Redis Value (期望): " + userId);
                System.out.println("6. 验证用户ID一致性: 通过");
                System.out.println("✅ 认证成功!");
                
            } else {
                System.out.println("❌ 缺少必要字段，无法生成Redis Key");
            }
            
        } else {
            System.out.println("❌ JWT解析失败");
        }
    }
    
    /**
     * 解析JWT Payload
     */
    private static JSONObject parseJwtPayload(String token) {
        try {
            String realToken = token.replace("Bearer ", "").trim();
            String[] parts = realToken.split("\\.");
            
            if (parts.length != 3) {
                System.out.println("❌ Token格式错误，不是有效的JWT (分段数: " + parts.length + ")");
                return null;
            }
            
            // 解码Payload
            String payloadBase64 = parts[1];
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            
            return JSON.parseObject(payloadJson);
            
        } catch (Exception e) {
            System.out.println("❌ JWT解析异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 计算MD5
     */
    private static String calculateMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (Exception e) {
            System.out.println("❌ MD5计算失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取设备类型描述
     */
    private static String getDeviceTypeDescription(int code) {
        switch (code) {
            case 1: return "android";
            case 2: return "ios";
            case 3: return "小程序";
            case 4: return "web";
            default: return "未知设备类型";
        }
    }
}
