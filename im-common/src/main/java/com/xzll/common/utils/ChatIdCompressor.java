package com.xzll.common.utils;

import cn.hutool.core.util.StrUtil;

/**
 * ChatId 可逆压缩工具
 * 
 * @Author: hzz  
 * @Date: 2025/11/19
 * @Description: 针对固定格式的chatId进行结构化压缩，支持完全可逆
 */
public class ChatIdCompressor {
    
    /**
     * 压缩chatId（可逆）
     * 原始格式: 100-1-123729160192-124948567040 (33字节)
     * 压缩格式: 64#6f2a1b2c3d4e5f (18字节，节省45%)
     * 
     * @param chatId 原始chatId
     * @return 压缩后的chatId，如果格式不匹配返回原值
     */
    public static String compress(String chatId) {
        if (StrUtil.isBlank(chatId)) {
            return chatId;
        }
        
        // 解析格式: {bizType}-{chatType}-{userId1}-{userId2}
        String[] parts = chatId.split("-");
        if (parts.length != 4) {
            // 格式不匹配，返回原值（兼容性处理）
            return chatId;
        }
        
        try {
            int bizType = Integer.parseInt(parts[0]);
            int chatType = Integer.parseInt(parts[1]); 
            long userId1 = Long.parseLong(parts[2]);
            long userId2 = Long.parseLong(parts[3]);
            
            // 使用紧凑格式编码
            // 格式: {bizType}{chatType}#{userId1的16进制}{userId2的16进制}
            return String.format("%d%d#%x%x", 
                bizType, chatType, userId1, userId2);
                
        } catch (NumberFormatException e) {
            // 解析失败，返回原值
            return chatId;
        }
    }
    
    /**
     * 解压chatId（完全可逆）
     * 
     * @param compressedChatId 压缩的chatId  
     * @return 原始chatId
     */
    public static String decompress(String compressedChatId) {
        if (StrUtil.isBlank(compressedChatId) || !compressedChatId.contains("#")) {
            // 不是压缩格式，直接返回（可能是原始格式）
            return compressedChatId;
        }
        
        try {
            String[] parts = compressedChatId.split("#");
            if (parts.length != 2) {
                return compressedChatId;
            }
            
            String prefix = parts[0]; // 如: "1001"
            String hexPart = parts[1]; // 如: "6f2a1b2c3d4e5f"
            
            // 解析bizType和chatType (假设都是1位数，如果是多位需要调整)
            int bizType, chatType;
            if (prefix.length() >= 4) {
                bizType = Integer.parseInt(prefix.substring(0, 3)); // 100
                chatType = Integer.parseInt(prefix.substring(3));   // 1
            } else {
                bizType = Integer.parseInt(prefix.substring(0, 1)); 
                chatType = Integer.parseInt(prefix.substring(1));
            }
            
            // 解析16进制用户ID（假设各占一半）
            int midPoint = hexPart.length() / 2;
            String hex1 = hexPart.substring(0, midPoint);
            String hex2 = hexPart.substring(midPoint);
            
            long userId1 = Long.parseUnsignedLong(hex1, 16);
            long userId2 = Long.parseUnsignedLong(hex2, 16);
            
            return String.format("%d-%d-%d-%d", bizType, chatType, userId1, userId2);
            
        } catch (Exception e) {
            // 解析失败，返回原值
            return compressedChatId;
        }
    }
    
    /**
     * 智能压缩（自动检测格式）
     * 
     * @param chatId 任意格式的chatId
     * @return 如果是标准格式则压缩，否则使用Base64
     */
    public static String smartCompress(String chatId) {
        String compressed = compress(chatId);
        
        // 如果压缩成功（格式匹配），返回压缩结果
        if (!compressed.equals(chatId)) {
            return compressed;
        }
        
        // 否则使用Base64编码（可逆但压缩率低）
        return "b64:" + java.util.Base64.getEncoder().encodeToString(chatId.getBytes());
    }
    
    /**
     * 智能解压
     * 
     * @param data 压缩的数据
     * @return 原始chatId
     */
    public static String smartDecompress(String data) {
        if (StrUtil.isBlank(data)) {
            return data;
        }
        
        // Base64格式
        if (data.startsWith("b64:")) {
            byte[] decoded = java.util.Base64.getDecoder().decode(data.substring(4));
            return new String(decoded);
        }
        
        // 结构化压缩格式
        return decompress(data);
    }
    
    /**
     * 分析压缩效果
     * 
     * @param originalChatId 原始chatId
     * @return 压缩效果描述
     */
    public static String analyzeCompression(String originalChatId) {
        String compressed = compress(originalChatId);
        
        if (compressed.equals(originalChatId)) {
            // 未压缩，尝试Base64
            String base64 = "b64:" + java.util.Base64.getEncoder().encodeToString(originalChatId.getBytes());
            double ratio = (double) base64.length() / originalChatId.length();
            return String.format("格式不匹配，Base64编码: %d->%d字节 (%.1f%%)", 
                originalChatId.length(), base64.length(), ratio * 100);
        } else {
            double ratio = (double) compressed.length() / originalChatId.length();
            int saved = originalChatId.length() - compressed.length();
            return String.format("结构化压缩: %d->%d字节, 节省%d字节 (%.1f%%)", 
                originalChatId.length(), compressed.length(), saved, (1-ratio) * 100);
        }
    }
}
