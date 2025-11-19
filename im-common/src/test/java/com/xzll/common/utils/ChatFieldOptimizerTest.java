package com.xzll.common.utils;

/**
 * 混合优化方案测试演示
 */
public class ChatFieldOptimizerTest {

    public static void main(String[] args) {
        // 你的实际chatId
        String testChatId = "100-1-123729160192-124948567040";
        
        System.out.println("=== 混合优化方案演示 ===");
        System.out.println("原始ChatId: " + testChatId + " (长度: " + testChatId.length() + ")");
        System.out.println();
        
        // 测试各字段优化效果
        testFieldOptimization(testChatId, ":unread", "Hash");
        testFieldOptimization(testChatId, ":clear_ts", "Hash"); 
        testFieldOptimization(testChatId, ":meta", "压缩");
        
        System.out.println();
        System.out.println(ChatFieldOptimizer.analyzeOptimization(testChatId));
        
        System.out.println();
        System.out.println("=== 反解能力测试 ===");
        testReversibility(testChatId);
    }
    
    private static void testFieldOptimization(String chatId, String suffix, String strategy) {
        String originalField = chatId + suffix;
        String optimizedField = ChatFieldOptimizer.buildOptimizedField(chatId, suffix);
        
        int saved = originalField.length() - optimizedField.length();
        double ratio = (double) saved / originalField.length() * 100;
        
        System.out.printf("%-12s (%s): %s (%d字节) -> %s (%d字节), 节省%d字节 (%.1f%%)\n", 
            suffix, strategy, originalField, originalField.length(), 
            optimizedField, optimizedField.length(), saved, ratio);
    }
    
    private static void testReversibility(String originalChatId) {
        String[] suffixes = {":unread", ":clear_ts", ":meta"};
        
        for (String suffix : suffixes) {
            String optimizedField = ChatFieldOptimizer.buildOptimizedField(originalChatId, suffix);
            
            if (ChatFieldOptimizer.isReversibleField(suffix)) {
                // meta字段测试反解
                String extractedChatId = ChatFieldOptimizer.extractChatIdFromField(optimizedField, suffix);
                boolean success = originalChatId.equals(extractedChatId);
                System.out.printf("%-12s: 反解chatId -> %s [%s]\n", 
                    suffix, extractedChatId, success ? "✅成功" : "❌失败");
            } else {
                // unread/clear_ts字段测试hash一致性
                String hash1 = ChatFieldOptimizer.hashChatId(originalChatId);
                String hash2 = ChatFieldOptimizer.extractHashFromField(optimizedField, suffix);
                boolean success = hash1.equals(hash2);
                System.out.printf("%-12s: hash一致性 -> %s vs %s [%s]\n", 
                    suffix, hash1, hash2, success ? "✅成功" : "❌失败");
            }
        }
        
        System.out.println();
        System.out.println("=== 实际应用场景 ===");
        System.out.println("✅ meta字段: 可反解完整chatId用于拼接HBase rowkey");
        System.out.println("✅ unread字段: 使用hash作为Redis标识，节省最大空间");
        System.out.println("✅ clear_ts字段: 使用hash作为Redis标识，节省最大空间");
        System.out.println("✅ 兼容性: 旧数据可以通过字段长度自动识别并兼容");
    }
}
