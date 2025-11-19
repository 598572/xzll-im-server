package com.xzll.common.utils;

/**
 * 压缩性能测试
 */
public class CompressionPerformanceTest {

    public static void main(String[] args) {
        String testChatId = "100-1-123729160192-124948567040";
        
        System.out.println("=== ChatId压缩性能测试 ===");
        System.out.println("测试ChatId: " + testChatId);
        System.out.println();
        
        // 测试结构化压缩性能
        testStructuralCompressionPerformance(testChatId);
        
        System.out.println();
        
        // 对比不压缩方案
        compareWithoutCompression(testChatId);
    }
    
    private static void testStructuralCompressionPerformance(String chatId) {
        System.out.println("=== 结构化压缩性能 ===");
        
        int iterations = 100000; // 10万次测试
        
        // 压缩性能测试
        long startTime = System.nanoTime();
        String compressed = null;
        for (int i = 0; i < iterations; i++) {
            compressed = ChatIdCompressor.compress(chatId);
        }
        long compressTime = System.nanoTime() - startTime;
        
        // 解压性能测试
        startTime = System.nanoTime();
        String decompressed = null;
        for (int i = 0; i < iterations; i++) {
            decompressed = ChatIdCompressor.decompress(compressed);
        }
        long decompressTime = System.nanoTime() - startTime;
        
        System.out.printf("压缩性能: %d次操作耗时 %.2f ms, 平均 %.2f μs/次\n", 
            iterations, compressTime / 1_000_000.0, compressTime / 1000.0 / iterations);
        System.out.printf("解压性能: %d次操作耗时 %.2f ms, 平均 %.2f μs/次\n", 
            iterations, decompressTime / 1_000_000.0, decompressTime / 1000.0 / iterations);
        
        System.out.println("压缩结果: " + chatId + " -> " + compressed);
        System.out.println("解压结果: " + compressed + " -> " + decompressed);
        System.out.println("可逆性: " + (chatId.equals(decompressed) ? "✅" : "❌"));
        
        // 分析性能特点
        System.out.println("\n性能分析:");
        System.out.println("- 压缩算法: 纯字符串格式转换，无复杂计算");
        System.out.println("- 压缩速度: ~" + String.format("%.1f", compressTime / 1000.0 / iterations) + "μs/次 (极快)");
        System.out.println("- 解压速度: ~" + String.format("%.1f", decompressTime / 1000.0 / iterations) + "μs/次 (极快)");
        System.out.println("- 性能瓶颈: 几乎无瓶颈，主要是字符串操作");
    }
    
    private static void compareWithoutCompression(String chatId) {
        System.out.println("=== 不压缩方案对比 ===");
        
        String suffix = ":meta";
        
        // 方案1: 压缩chatId
        String compressedField = ChatIdCompressor.compress(chatId) + suffix;
        
        // 方案2: 纯hash (无法反解)
        String hashField = ChatFieldOptimizer.hashChatId(chatId) + suffix;
        
        // 方案3: 不压缩 (原始)
        String originalField = chatId + suffix;
        
        System.out.printf("方案1 (压缩): %s (%d字节) - 可反解 ✅\n", 
            compressedField, compressedField.length());
        System.out.printf("方案2 (Hash):  %s (%d字节) - 不可反解 ❌\n", 
            hashField, hashField.length());
        System.out.printf("方案3 (原始): %s (%d字节) - 可反解 ✅\n", 
            originalField, originalField.length());
        
        System.out.println("\n空间对比:");
        int saved1 = originalField.length() - compressedField.length();
        int saved2 = originalField.length() - hashField.length();
        
        System.out.printf("压缩方案: 节省 %d字节 (%.1f%%), 可反解\n", 
            saved1, (double)saved1/originalField.length()*100);
        System.out.printf("Hash方案: 节省 %d字节 (%.1f%%), 不可反解\n", 
            saved2, (double)saved2/originalField.length()*100);
        
        System.out.println("\n推荐方案:");
        System.out.println("✅ 如果需要反解chatId: 使用压缩方案 (性能极好)");
        System.out.println("✅ 如果不需要反解: 使用Hash方案 (空间最优)");
        System.out.println("❌ 避免使用原始方案 (浪费空间)");
    }
}
