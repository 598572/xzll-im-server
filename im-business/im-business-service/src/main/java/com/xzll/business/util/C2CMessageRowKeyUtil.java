package com.xzll.business.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @Author: hzz
 * @Date: 2025/10/30
 * @Description: C2C消息HBase RowKey工具类
 * 
 * RowKey设计规则：
 * - 格式：{chatId}_{msgId}
 * - 示例：user1-user2_123456789012
 * - 优势：利用chatId前缀实现范围查询，msgId保证时间有序
 */
@Slf4j
public class C2CMessageRowKeyUtil {

    /**
     * RowKey分隔符
     */
    public static final String ROW_KEY_SEPARATOR = "_";
    
    /**
     * RowKey最大长度限制
     */
    private static final int MAX_ROW_KEY_LENGTH = 1000;
    
    /**
     * chatId最大长度限制
     */
    private static final int MAX_CHAT_ID_LENGTH = 200;
    
    /**
     * msgId最大长度限制
     */
    private static final int MAX_MSG_ID_LENGTH = 50;

    /**
     * 生成C2C消息的RowKey
     * 
     * @param chatId 会话ID，格式如：user1-user2
     * @param msgId 消息ID，雪花算法生成的唯一ID
     * @return RowKey字符串
     * @throws IllegalArgumentException 当参数无效时
     */
    public static String generateRowKey(String chatId, String msgId) {
        // 参数验证
        validateChatId(chatId);
        validateMsgId(msgId);
        
        String rowKey = chatId + ROW_KEY_SEPARATOR + msgId;
        
        // 长度验证
        if (rowKey.length() > MAX_ROW_KEY_LENGTH) {
            throw new IllegalArgumentException(
                String.format("RowKey长度超限: %d > %d, rowKey=%s", 
                    rowKey.length(), MAX_ROW_KEY_LENGTH, rowKey));
        }
        
        return rowKey;
    }

    /**
     * 从RowKey中解析chatId和msgId
     * 
     * @param rowKey HBase的RowKey
     * @return RowKeyInfo对象，包含chatId和msgId
     * @throws IllegalArgumentException 当RowKey格式无效时
     */
    public static RowKeyInfo parseRowKey(String rowKey) {
        if (StringUtils.isBlank(rowKey)) {
            throw new IllegalArgumentException("RowKey不能为空");
        }
        
        // 使用分隔符分割，限制分割次数为2，避免msgId中包含分隔符时出错
        String[] parts = rowKey.split(ROW_KEY_SEPARATOR, 2);
        
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                String.format("无效的RowKey格式，期望格式: chatId%smsgId, 实际: %s", 
                    ROW_KEY_SEPARATOR, rowKey));
        }
        
        String chatId = parts[0];
        String msgId = parts[1];
        
        // 验证解析后的数据
        if (StringUtils.isBlank(chatId)) {
            throw new IllegalArgumentException("从RowKey中解析的chatId为空: " + rowKey);
        }
        
        if (StringUtils.isBlank(msgId)) {
            throw new IllegalArgumentException("从RowKey中解析的msgId为空: " + rowKey);
        }
        
        return new RowKeyInfo(chatId, msgId, rowKey);
    }

    /**
     * 生成会话前缀，用于HBase范围查询
     * 
     * @param chatId 会话ID
     * @return 会话前缀字符串，格式：{chatId}_
     */
    public static String generateChatPrefix(String chatId) {
        validateChatId(chatId);
        return chatId + ROW_KEY_SEPARATOR;
    }

    /**
     * 生成会话范围查询的开始RowKey
     * 
     * @param chatId 会话ID
     * @return 开始RowKey
     */
    public static String generateChatStartRow(String chatId) {
        return generateChatPrefix(chatId);
    }

    /**
     * 生成会话范围查询的结束RowKey
     * 
     * @param chatId 会话ID
     * @return 结束RowKey
     */
    public static String generateChatEndRow(String chatId) {
        validateChatId(chatId);
        // 使用字符的最大值作为结束边界，确保包含所有该chatId的消息
        return chatId + ROW_KEY_SEPARATOR + Character.MAX_VALUE;
    }

    /**
     * 生成时间范围查询的RowKey
     * 
     * @param chatId 会话ID
     * @param msgId 消息ID（时间边界）
     * @return 时间范围RowKey
     */
    public static String generateTimeRangeRowKey(String chatId, String msgId) {
        return generateRowKey(chatId, msgId);
    }

    /**
     * 检查RowKey是否属于指定会话
     * 
     * @param rowKey HBase RowKey
     * @param chatId 会话ID
     * @return 是否属于该会话
     */
    public static boolean belongsToChat(String rowKey, String chatId) {
        if (StringUtils.isBlank(rowKey) || StringUtils.isBlank(chatId)) {
            return false;
        }
        
        try {
            RowKeyInfo info = parseRowKey(rowKey);
            return chatId.equals(info.getChatId());
        } catch (Exception e) {
            log.warn("检查RowKey归属失败: rowKey={}, chatId={}", rowKey, chatId, e);
            return false;
        }
    }

    /**
     * 验证RowKey格式是否正确
     * 
     * @param rowKey HBase RowKey
     * @return 是否有效
     */
    public static boolean isValidRowKey(String rowKey) {
        try {
            parseRowKey(rowKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证chatId的有效性
     */
    private static void validateChatId(String chatId) {
        if (StringUtils.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId不能为空");
        }
        
        if (chatId.length() > MAX_CHAT_ID_LENGTH) {
            throw new IllegalArgumentException(
                String.format("chatId长度超限: %d > %d, chatId=%s", 
                    chatId.length(), MAX_CHAT_ID_LENGTH, chatId));
        }
        
        if (chatId.contains(ROW_KEY_SEPARATOR)) {
            throw new IllegalArgumentException(
                String.format("chatId不能包含分隔符'%s': %s", ROW_KEY_SEPARATOR, chatId));
        }
    }

    /**
     * 验证msgId的有效性
     */
    private static void validateMsgId(String msgId) {
        if (StringUtils.isBlank(msgId)) {
            throw new IllegalArgumentException("msgId不能为空");
        }
        
        if (msgId.length() > MAX_MSG_ID_LENGTH) {
            throw new IllegalArgumentException(
                String.format("msgId长度超限: %d > %d, msgId=%s", 
                    msgId.length(), MAX_MSG_ID_LENGTH, msgId));
        }
    }

    /**
     * RowKey信息封装类
     */
    public static class RowKeyInfo {
        private final String chatId;
        private final String msgId;
        private final String originalRowKey;

        public RowKeyInfo(String chatId, String msgId, String originalRowKey) {
            this.chatId = chatId;
            this.msgId = msgId;
            this.originalRowKey = originalRowKey;
        }

        public String getChatId() {
            return chatId;
        }

        public String getMsgId() {
            return msgId;
        }

        public String getOriginalRowKey() {
            return originalRowKey;
        }

        @Override
        public String toString() {
            return String.format("RowKeyInfo{chatId='%s', msgId='%s', rowKey='%s'}", 
                chatId, msgId, originalRowKey);
        }
    }

    /**
     * RowKey构建器，支持链式调用
     */
    public static class RowKeyBuilder {
        private String chatId;
        private String msgId;

        public static RowKeyBuilder create() {
            return new RowKeyBuilder();
        }

        public RowKeyBuilder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public RowKeyBuilder msgId(String msgId) {
            this.msgId = msgId;
            return this;
        }

        public String build() {
            return generateRowKey(chatId, msgId);
        }
    }
}
