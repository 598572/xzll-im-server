package com.xzll.connect.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xzll.common.constant.enums.ImTerminalType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Token测试主类
 * 用于调试和验证Token解析功能
 */
public class TokenTestMain {
    
    public static void main(String[] args) {
        // 你提供的实际Token
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJ4emxsYWRnMDIiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxOTY1Njg3MDczOTEyNTI0ODAwLCJleHAiOjE3NTc1MTIxMTEsImF1dGhvcml0aWVzIjpbIkFETUlOIl0sImp0aSI6IjlmYmZlNDFhLTI5MzktNDI2Ny1iODUwLTU2NDM5ZGJjNjliMyIsImNsaWVudF9pZCI6ImNsaWVudC1hcHAifQ.zR9UE9x0CnX_yD4RqNB8bWjFzcCwlFOiiEM1tiJ23kr7CPA6UOLya-xyrVpGKxbSJf7HTOPLZwQNjR3yOyr3T3SII9qd-ja6-Qisv6U8cBcgezQ4NwILxBc165mGol0rf5SgLy9TNUOXXp8uT_su1ZI8VvpyA8d7ZeXP8bfy6gF9cULXA2sjhpB2Q5Uz7APFJKX7dbVY8MyFyWE6nkE-ER4o6zZnTJQ4oBnljb_ovhuh-Zennv8MuUhuiOVDdoVeEE4CPnW-rGcnkut4KY8MVxYF-3M0NQC5cjdH4JZ8CYvKThkH9_51_sR6geMq5oqCpvHIt6_wzKeQrduu5RyzIA";
        
        System.out.println("========== Token解析测试 ==========");
        System.out.println("Token: " + token);
        System.out.println();
        
        // 1. 测试Token格式验证
        testTokenFormat(token);
        
        // 2. 手动解析JWT Payload
        manualParseJwtPayload(token);
        
        // 3. 测试现有的TokenUtils解析
        testTokenUtilsParsing(token);
        
        // 4. 测试MD5计算
        testMd5Calculation(token);
        
        // 5. 测试完整流程
        testCompleteFlow(token);
    }
    
    /**
     * 测试Token格式验证
     */
    private static void testTokenFormat(String token) {
        System.out.println("========== 1. Token格式验证 ==========");
        
        boolean isValidFormat = TokenUtils.isValidJwtFormat(token);
        System.out.println("Token格式验证: " + (isValidFormat ? "✅ 通过" : "❌ 失败"));
        
        // 检查Token结构
        String realToken = token.replace("Bearer ", "").trim();
        String[] parts = realToken.split("\\.");
        System.out.println("Token分段数量: " + parts.length);
        if (parts.length == 3) {
            System.out.println("Header长度: " + parts[0].length());
            System.out.println("Payload长度: " + parts[1].length());
            System.out.println("Signature长度: " + parts[2].length());
        }
        System.out.println();
    }
    
    /**
     * 手动解析JWT Payload
     */
    private static void manualParseJwtPayload(String token) {
        System.out.println("========== 2. 手动解析JWT Payload ==========");
        
        try {
            String realToken = token.replace("Bearer ", "").trim();
            String[] parts = realToken.split("\\.");
            
            if (parts.length != 3) {
                System.out.println("❌ Token格式错误");
                return;
            }
            
            // 解码Payload
            String payloadBase64 = parts[1];
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            
            System.out.println("Payload JSON: " + payloadJson);
            
            // 解析JSON
            JSONObject payload = JSON.parseObject(payloadJson);
            System.out.println("解析后的字段:");
            payload.forEach((key, value) -> {
                System.out.println("  " + key + " = " + value + " (" + value.getClass().getSimpleName() + ")");
            });
            
            // 检查关键字段
            System.out.println("\n关键字段检查:");
            System.out.println("  user_id: " + payload.get("user_id"));
            System.out.println("  id: " + payload.get("id"));
            System.out.println("  device_type: " + payload.get("device_type"));
            
        } catch (Exception e) {
            System.out.println("❌ 手动解析失败: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
    
    /**
     * 测试现有的TokenUtils解析
     */
    private static void testTokenUtilsParsing(String token) {
        System.out.println("========== 3. TokenUtils解析测试 ==========");
        
        // 测试解析用户ID
        String userId = TokenUtils.parseUserIdFromToken(token);
        System.out.println("解析的用户ID: " + userId);
        
        // 测试解析设备类型
        ImTerminalType deviceType = TokenUtils.parseDeviceTypeFromToken(token);
        System.out.println("解析的设备类型: " + deviceType);
        
        // 测试完整解析
        TokenUtils.TokenInfo tokenInfo = TokenUtils.parseTokenInfo(token);
        if (tokenInfo != null) {
            System.out.println("✅ 完整解析成功:");
            System.out.println("  用户ID: " + tokenInfo.getUserId());
            System.out.println("  设备类型: " + tokenInfo.getDeviceType());
            System.out.println("  Token MD5: " + tokenInfo.getTokenMd5());
        } else {
            System.out.println("❌ 完整解析失败");
        }
        System.out.println();
    }
    
    /**
     * 测试MD5计算
     */
    private static void testMd5Calculation(String token) {
        System.out.println("========== 4. MD5计算测试 ==========");
        
        String md5 = TokenUtils.calculateTokenMd5(token);
        System.out.println("Token MD5: " + md5);
        System.out.println("MD5长度: " + (md5 != null ? md5.length() : "null"));
        System.out.println();
    }
    
    /**
     * 测试完整流程
     */
    private static void testCompleteFlow(String token) {
        System.out.println("========== 5. 完整流程测试 ==========");
        
        TokenUtils.TokenInfo tokenInfo = TokenUtils.parseTokenInfo(token);
        
        if (tokenInfo != null) {
            System.out.println("✅ Token解析成功!");
            System.out.println("TokenInfo: " + tokenInfo.toString());
            
            // 生成Redis Key
            String redisKey = tokenInfo.buildRedisKey("userLogin:token:");
            System.out.println("生成的Redis Key: " + redisKey);
            
            // 模拟Redis验证流程
            System.out.println("\n模拟Redis验证:");
            System.out.println("1. 查找Redis Key: " + redisKey);
            System.out.println("2. 期望Value: " + tokenInfo.getUserId());
            System.out.println("3. 验证用户ID一致性: 通过");
            
        } else {
            System.out.println("❌ Token解析失败!");
            
            // 分析失败原因
            System.out.println("\n失败原因分析:");
            
            boolean formatOk = TokenUtils.isValidJwtFormat(token);
            String userId = TokenUtils.parseUserIdFromToken(token);
            ImTerminalType deviceType = TokenUtils.parseDeviceTypeFromToken(token);
            String md5 = TokenUtils.calculateTokenMd5(token);
            
            System.out.println("  格式验证: " + (formatOk ? "✅" : "❌"));
            System.out.println("  用户ID解析: " + (userId != null ? "✅ " + userId : "❌"));
            System.out.println("  设备类型解析: " + (deviceType != null ? "✅ " + deviceType : "❌"));
            System.out.println("  MD5计算: " + (md5 != null ? "✅" : "❌"));
        }
        System.out.println();
    }
}
