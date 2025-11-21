package com.xzll.common.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

import java.util.zip.CRC32;

/**
 * Redis Key 优化工具类
 * 
 * @Author: hzz
 * @Date: 2025/11/19
 * @Description: 用于优化Redis key长度，减少内存占用
 */
public class KeyOptimizeUtil {
    
    /**
     * 使用MD5 hash优化chatId（取前8位）
     * 优点：碰撞概率极低
     * 缺点：略慢，但可接受
     * 
     * @param chatId 原始chatId
     * @return 8位hash字符串
     */
    public static String hashChatId(String chatId) {
        if (StrUtil.isBlank(chatId)) {
            return "";
        }
        return DigestUtil.md5Hex(chatId).substring(0, 8);
    }
    
    /**
     * 使用CRC32 hash优化chatId
     * 优点：速度快
     * 缺点：32位可能有碰撞风险（但在实际场景中概率很低）
     * 
     * @param chatId 原始chatId  
     * @return CRC32 hash值的hex字符串（8位）
     */
    public static String crc32ChatId(String chatId) {
        if (StrUtil.isBlank(chatId)) {
            return "";
        }
        CRC32 crc32 = new CRC32();
        crc32.update(chatId.getBytes());
        return String.format("%08x", crc32.getValue());
    }
    
    /**
     * 生成优化的Redis hash field
     * 格式：{hash8}:{suffix}
     * 例如：a1b2c3d4:unread
     * 
     * @param chatId 原始chatId
     * @param suffix 后缀（如 ":unread", ":meta", ":clear_ts"）
     * @return 优化后的field名称
     */
    public static String buildOptimizedField(String chatId, String suffix) {
        return hashChatId(chatId) + suffix;
    }
    
    /**
     * 构建chatId映射表的Redis key
     * 格式：chat:hash_map:{userId}
     * 
     * @param userId 用户ID
     * @return 映射表Redis key
     */
    public static String buildHashMapKey(String userId) {
        return "chat:hash_map:" + userId;
    }
    
    /**
     * 从优化的field中提取hash值
     * 
     * @param optimizedField 优化后的field（如 "a1b2c3d4:unread"）
     * @param suffix 后缀（如 ":unread"）
     * @return hash值（如 "a1b2c3d4"），如果格式不匹配返回null
     */
    public static String extractHashFromField(String optimizedField, String suffix) {
        if (StrUtil.isBlank(optimizedField) || !optimizedField.endsWith(suffix)) {
            return null;
        }
        return optimizedField.substring(0, optimizedField.length() - suffix.length());
    }
    
    /**
     * 校验两个chatId是否匹配（通过hash比较）
     * 
     * @param chatId1 chatId1
     * @param chatId2 chatId2  
     * @return true=匹配，false=不匹配
     */
    public static boolean isChatIdMatch(String chatId1, String chatId2) {
        return hashChatId(chatId1).equals(hashChatId(chatId2));
    }
    
    /**
     * 估算key长度优化效果
     * 
     * @param originalChatId 原始chatId
     * @param suffix 后缀
     * @return 优化效果描述
     */
    public static String analyzeOptimization(String originalChatId, String suffix) {
        String original = originalChatId + suffix;
        String optimized = buildOptimizedField(originalChatId, suffix);
        
        int originalLen = original.length();
        int optimizedLen = optimized.length();
        double ratio = (double) optimizedLen / originalLen;
        int saved = originalLen - optimizedLen;
        
        return String.format("原始: %d字节 -> 优化: %d字节, 节省: %d字节 (%.1f%%)", 
            originalLen, optimizedLen, saved, (1 - ratio) * 100);
    }

    /**
     * 计算优化的总体效益（双向映射表方案）
     * 
     * @param originalChatId 原始chatId
     * @param fieldCount 该chatId对应的field数量（通常为3：unread+meta+clear_ts）
     * @return 总体节省的字节数
     */
    public static int calculateTotalSavings(String originalChatId, int fieldCount) {
        // 每个field节省的字节数
        int bytesPerField = originalChatId.length() - 8;
        int totalFieldSavings = bytesPerField * fieldCount;
        
        // 映射表一次性开销（hash8 -> originalChatId）
        int mapOverhead = 8 + originalChatId.length() + 8; // hash + chatId + Redis开销
        
        // 净节省 = field节省 - 映射表开销
        return totalFieldSavings - mapOverhead;
    }
    
    /**
     * 检查是否值得优化（当净节省>0时值得优化）
     * 
     * @param originalChatId 原始chatId
     * @param fieldCount field数量
     * @return true=值得优化
     */
    public static boolean isWorthOptimizing(String originalChatId, int fieldCount) {
        return calculateTotalSavings(originalChatId, fieldCount) > 0;
    }
}
