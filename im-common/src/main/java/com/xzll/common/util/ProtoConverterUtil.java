package com.xzll.common.util;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Protobuf 优化相关的转换工具类
 * 用于处理UUID与bytes、String与fixed64之间的转换
 * 
 * @author xzll
 * @date 2025-11-13
 */
public class ProtoConverterUtil {

    /**
     * 将UUID字符串转换为16字节的ByteString（用于protobuf）
     * 格式：UUID "550e8400-e29b-41d4-a716-446655440000" -> 16字节bytes
     *
     * @param uuidStr UUID字符串（36字节，包含连字符）
     * @return ByteString（16字节）
     */
    public static ByteString uuidStringToBytes(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) {
            return ByteString.EMPTY;
        }
        
        try {
            UUID uuid = UUID.fromString(uuidStr);
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return ByteString.copyFrom(bb.array());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid UUID string: " + uuidStr, e);
        }
    }

    /**
     * 将16字节的ByteString转换回UUID字符串
     *
     * @param bytes ByteString（16字节）
     * @return UUID字符串（36字节）
     */
    public static String bytesToUuidString(ByteString bytes) {
        if (bytes == null || bytes.isEmpty()) {
            return "";
        }
        
        if (bytes.size() != 16) {
            throw new IllegalArgumentException("UUID bytes must be 16 bytes, got: " + bytes.size());
        }
        
        try {
            ByteBuffer bb = ByteBuffer.wrap(bytes.toByteArray());
            long mostSigBits = bb.getLong();
            long leastSigBits = bb.getLong();
            UUID uuid = new UUID(mostSigBits, leastSigBits);
            return uuid.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid UUID bytes", e);
        }
    }

    /**
     * 将String类型的雪花ID转换为long（用于proto的fixed64）
     *
     * @param snowflakeIdStr 雪花ID字符串（如 "1988484031183061064"）
     * @return long值
     */
    public static long snowflakeStringToLong(String snowflakeIdStr) {
        if (snowflakeIdStr == null || snowflakeIdStr.isEmpty()) {
            return 0L;
        }
        
        try {
            return Long.parseLong(snowflakeIdStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid snowflake ID string: " + snowflakeIdStr, e);
        }
    }

    /**
     * 将long类型的雪花ID转换为String
     *
     * @param snowflakeId 雪花ID
     * @return String值
     */
    public static String longToSnowflakeString(long snowflakeId) {
        if (snowflakeId == 0L) {
            return "";
        }
        return String.valueOf(snowflakeId);
    }

    /**
     * 生成chatId（从fromUserId和toUserId）
     * 格式：100-1-{smaller_userId}-{larger_userId}
     * 注意：为了保证一对用户的chatId唯一，较小的ID总是在前面
     *
     * @param fromUserId 发送人ID
     * @param toUserId   接收人ID
     * @return chatId
     */
    public static String generateChatId(String fromUserId, String toUserId) {
        if (fromUserId == null || toUserId == null) {
            throw new IllegalArgumentException("fromUserId and toUserId cannot be null");
        }
        
        // 将两个ID按字符串大小排序，保证chatId的唯一性
        String smaller, larger;
        if (fromUserId.compareTo(toUserId) < 0) {
            smaller = fromUserId;
            larger = toUserId;
        } else {
            smaller = toUserId;
            larger = fromUserId;
        }
        
        // 格式：100-1-{smaller_userId}-{larger_userId}
        return "100-1-" + smaller + "-" + larger;
    }

    /**
     * 生成chatId（从long类型的雪花ID）
     *
     * @param fromUserId 发送人ID（long）
     * @param toUserId   接收人ID（long）
     * @return chatId
     */
    public static String generateChatId(long fromUserId, long toUserId) {
        return generateChatId(String.valueOf(fromUserId), String.valueOf(toUserId));
    }
}

