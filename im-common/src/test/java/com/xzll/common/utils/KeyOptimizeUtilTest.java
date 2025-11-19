package com.xzll.common.utils;

import org.junit.jupiter.api.Test;

/**
 * Redis Key 优化测试
 */
public class KeyOptimizeUtilTest {

    @Test
    public void testKeyOptimization() {
        // 模拟你的长chatId
        String longChatId = "100-1-123729160192-124948567040";
        String suffix = ":unread";
        
        // 优化效果演示
        System.out.println("=== Redis Key 优化效果 ===");
        System.out.println("原始 chatId: " + longChatId);
        System.out.println("原始 field: " + longChatId + suffix + " (长度: " + (longChatId + suffix).length() + "字节)");
        
        String optimizedField = KeyOptimizeUtil.buildOptimizedField(longChatId, suffix);
        System.out.println("优化 field: " + optimizedField + " (长度: " + optimizedField.length() + "字节)");
        
        System.out.println("\n" + KeyOptimizeUtil.analyzeOptimization(longChatId, suffix));
        
        // 测试不同后缀的优化效果
        System.out.println("\n=== 各种后缀优化效果 ===");
        testSuffix(longChatId, ":unread");
        testSuffix(longChatId, ":meta");  
        testSuffix(longChatId, ":clear_ts");
        
        // 测试hash一致性
        System.out.println("\n=== Hash一致性验证 ===");
        String hash1 = KeyOptimizeUtil.hashChatId(longChatId);
        String hash2 = KeyOptimizeUtil.hashChatId(longChatId);
        System.out.println("同一chatId多次hash结果: " + hash1.equals(hash2));
        
        // 测试不同chatId的hash碰撞概率（理论测试）
        System.out.println("\n=== Hash碰撞概率测试 ===");
        String[] testChatIds = {
            "100-1-123729160192-124948567040",
            "100-1-123729160193-124948567041", 
            "100-1-123729160194-124948567042",
            "100-2-123729160192-124948567040",
            "200-1-123729160192-124948567040"
        };
        
        for (String chatId : testChatIds) {
            String hash = KeyOptimizeUtil.hashChatId(chatId);
            System.out.println("ChatId: " + chatId + " -> Hash: " + hash);
        }
    }
    
    private void testSuffix(String chatId, String suffix) {
        String original = chatId + suffix;
        String optimized = KeyOptimizeUtil.buildOptimizedField(chatId, suffix);
        
        int saved = original.length() - optimized.length();
        double ratio = (double) saved / original.length() * 100;
        
        System.out.printf("%-12s: %s -> %s, 节省%d字节(%.1f%%)\n", 
            suffix, original, optimized, saved, ratio);
    }
}
