package com.xzll.common.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

/**
 * 聊天字段优化器 - 混合优化方案
 * 
 * @Author: hzz
 * @Date: 2025/11/19
 * @Description: 
 * - unread/clear_ts: 使用hash (不需要反解)
 * - meta: 使用可逆压缩 (需要反解出chatId拼接rowkey)
 */
public class ChatFieldOptimizer {

    private static final String META_SUFFIX = ":meta";
    private static final String UNREAD_SUFFIX = ":unread"; 
    private static final String CLEAR_TS_SUFFIX = ":clear_ts";

    /**
     * 生成优化的field名称（混合策略）
     * 
     * @param chatId 原始chatId
     * @param suffix 字段后缀
     * @return 优化后的field名称
     */
    public static String buildOptimizedField(String chatId, String suffix) {
        if (StrUtil.isBlank(chatId) || StrUtil.isBlank(suffix)) {
            return chatId + suffix;
        }

        if (META_SUFFIX.equals(suffix)) {
            // meta字段使用高性能压缩 (超快速压缩，完全可逆)
            return HighPerformanceChatIdCompressor.compress(chatId) + suffix;
        } else {
            // unread/clear_ts字段只需标识，使用hash
            return hashChatId(chatId) + suffix;
        }
    }

    /**
     * 从优化字段中提取chatId（仅适用于meta字段）
     * 
     * @param optimizedField 优化后的field
     * @param suffix 字段后缀
     * @return 原始chatId，如果是非meta字段或解析失败返回null
     */
    public static String extractChatIdFromField(String optimizedField, String suffix) {
        if (!META_SUFFIX.equals(suffix)) {
            // 非meta字段无法反解
            return null;
        }

        if (StrUtil.isBlank(optimizedField) || !optimizedField.endsWith(suffix)) {
            return null;
        }

        // meta字段高性能解压提取chatId
        String compressedChatId = optimizedField.substring(0, optimizedField.length() - suffix.length());
        return HighPerformanceChatIdCompressor.decompress(compressedChatId);
    }

    /**
     * 从优化字段中提取hash值（适用于unread/clear_ts字段）
     * 
     * @param optimizedField 优化后的field
     * @param suffix 字段后缀
     * @return hash值，如果格式不匹配返回null
     */
    public static String extractHashFromField(String optimizedField, String suffix) {
        if (META_SUFFIX.equals(suffix)) {
            // meta字段使用压缩，不是简单hash
            return null;
        }

        if (StrUtil.isBlank(optimizedField) || !optimizedField.endsWith(suffix)) {
            return null;
        }

        return optimizedField.substring(0, optimizedField.length() - suffix.length());
    }

    /**
     * 计算chatId的hash值（用于unread/clear_ts字段）
     * 
     * @param chatId 原始chatId
     * @return 8位hash值
     */
    public static String hashChatId(String chatId) {
        if (StrUtil.isBlank(chatId)) {
            return "";
        }
        return DigestUtil.md5Hex(chatId).substring(0, 8);
    }

    /**
     * 检查字段是否需要可逆（即是否为meta字段）
     * 
     * @param suffix 字段后缀
     * @return true=需要可逆(meta字段)，false=只需hash标识
     */
    public static boolean isReversibleField(String suffix) {
        return META_SUFFIX.equals(suffix);
    }

    /**
     * 分析混合优化效果
     * 
     * @param originalChatId 原始chatId
     * @return 优化效果报告
     */
    public static String analyzeOptimization(String originalChatId) {
        if (StrUtil.isBlank(originalChatId)) {
            return "无效的chatId";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== 混合优化方案效果 ===\n");
        report.append("原始ChatId: ").append(originalChatId)
              .append(" (").append(originalChatId.length()).append("字节)\n\n");

        int totalOriginal = 0;
        int totalOptimized = 0;

        // 分析各字段
        String[] suffixes = {UNREAD_SUFFIX, CLEAR_TS_SUFFIX, META_SUFFIX};
        String[] strategies = {"Hash", "Hash", "压缩"};

        for (int i = 0; i < suffixes.length; i++) {
            String suffix = suffixes[i];
            String strategy = strategies[i];
            
            String originalField = originalChatId + suffix;
            String optimizedField = buildOptimizedField(originalChatId, suffix);
            
            int originalLen = originalField.length();
            int optimizedLen = optimizedField.length();
            int saved = originalLen - optimizedLen;
            double ratio = (double) saved / originalLen * 100;
            
            totalOriginal += originalLen;
            totalOptimized += optimizedLen;
            
            report.append(String.format("%-12s (%s): %d->%d字节, 节省%d字节 (%.1f%%)\n", 
                suffix, strategy, originalLen, optimizedLen, saved, ratio));
        }

        int totalSaved = totalOriginal - totalOptimized;
        double totalRatio = (double) totalSaved / totalOriginal * 100;
        
        report.append(String.format("\n总计效果: %d->%d字节, 节省%d字节 (%.1f%%)", 
            totalOriginal, totalOptimized, totalSaved, totalRatio));
            
        return report.toString();
    }

    /**
     * 验证字段优化的一致性（用于测试）
     * 
     * @param chatId 原始chatId
     * @param suffix 字段后缀
     * @return 是否一致
     */
    public static boolean validateOptimization(String chatId, String suffix) {
        String optimizedField = buildOptimizedField(chatId, suffix);
        
        if (isReversibleField(suffix)) {
            // meta字段需要验证可逆性
            String extracted = extractChatIdFromField(optimizedField, suffix);
            return chatId.equals(extracted);
        } else {
            // unread/clear_ts字段验证hash一致性
            String hash1 = hashChatId(chatId);
            String hash2 = extractHashFromField(optimizedField, suffix);
            return hash1.equals(hash2);
        }
    }
}
