package com.xzll.common.utils;

import org.junit.jupiter.api.Test;

/**
 * ChatId可逆压缩测试
 */
public class ChatIdCompressorTest {

    @Test 
    public void testReversibleCompression() {
        // 测试你的实际chatId
        String originalChatId = "100-1-123729160192-124948567040";
        
        System.out.println("=== 可逆压缩测试 ===");
        System.out.println("原始ChatId: " + originalChatId + " (长度: " + originalChatId.length() + ")");
        
        // 压缩
        String compressed = ChatIdCompressor.compress(originalChatId);
        System.out.println("压缩结果: " + compressed + " (长度: " + compressed.length() + ")");
        
        // 解压
        String decompressed = ChatIdCompressor.decompress(compressed);  
        System.out.println("解压结果: " + decompressed + " (长度: " + decompressed.length() + ")");
        
        // 验证可逆性
        boolean isReversible = originalChatId.equals(decompressed);
        System.out.println("可逆性验证: " + (isReversible ? "✅ 成功" : "❌ 失败"));
        
        if (isReversible) {
            int saved = originalChatId.length() - compressed.length();
            double ratio = (double) saved / originalChatId.length() * 100;
            System.out.println("压缩效果: 节省 " + saved + " 字节 (" + String.format("%.1f", ratio) + "%)");
        }
        
        System.out.println("\n" + ChatIdCompressor.analyzeCompression(originalChatId));
        
        // 测试field优化效果
        testFieldOptimization(originalChatId, compressed);
    }
    
    private void testFieldOptimization(String original, String compressed) {
        System.out.println("\n=== Redis Field 优化对比 ===");
        
        String[] suffixes = {":unread", ":meta", ":clear_ts"};
        int totalSaved = 0;
        
        for (String suffix : suffixes) {
            String originalField = original + suffix;
            String compressedField = compressed + suffix;
            
            int saved = originalField.length() - compressedField.length();
            totalSaved += saved;
            
            System.out.printf("%-12s: %s (%d字节) -> %s (%d字节), 节省%d字节\n", 
                suffix, originalField, originalField.length(), 
                compressedField, compressedField.length(), saved);
        }
        
        System.out.println("总计节省: " + totalSaved + " 字节");
    }
    
    @Test
    public void testEdgeCases() {
        System.out.println("\n=== 边界情况测试 ===");
        
        String[] testCases = {
            "100-1-123729160192-124948567040",  // 标准格式
            "200-2-987654321098-123456789012",  // 不同数值
            "invalid-format",                   // 无效格式  
            "100-1-abc-def",                    // 非数字
            "",                                 // 空字符串
            null                                // null值
        };
        
        for (String testCase : testCases) {
            testRoundTrip(testCase);
        }
    }
    
    private void testRoundTrip(String original) {
        try {
            String compressed = ChatIdCompressor.compress(original);
            String decompressed = ChatIdCompressor.decompress(compressed);
            
            boolean success = (original == null && decompressed == null) || 
                            (original != null && original.equals(decompressed));
            
            System.out.printf("输入: %-35s -> 压缩: %-20s -> 解压: %-35s [%s]\n", 
                String.valueOf(original), compressed, String.valueOf(decompressed), 
                success ? "✅" : "❌");
                
        } catch (Exception e) {
            System.out.printf("输入: %-35s -> 异常: %s\n", String.valueOf(original), e.getMessage());
        }
    }
    
    @Test
    public void testSmartCompression() {
        System.out.println("\n=== 智能压缩测试 ===");
        
        String[] testCases = {
            "100-1-123729160192-124948567040",    // 标准格式 -> 结构化压缩
            "some-random-chat-id-format",         // 非标准 -> Base64
            "user123-conversation456"             // 非标准 -> Base64
        };
        
        for (String testCase : testCases) {
            String compressed = ChatIdCompressor.smartCompress(testCase);
            String decompressed = ChatIdCompressor.smartDecompress(compressed);
            
            boolean success = testCase.equals(decompressed);
            
            System.out.printf("原始: %s\n", testCase);
            System.out.printf("压缩: %s\n", compressed);  
            System.out.printf("解压: %s\n", decompressed);
            System.out.printf("结果: %s\n\n", success ? "✅ 成功" : "❌ 失败");
        }
    }
}
