package com.xzll.business.service;

import java.util.Map;

/**
 * 会话列表管理服务
 * 使用Redis存储会话列表元数据（msgId + 未读数 + 时间戳）
 * 
 * Redis结构：
 * Key: chat:list:{userId}
 * Type: Hash
 * Field: {chatId}
 * Value: LZ4压缩的JSON {"m":"msgId","u":5,"t":1700366400000,"f":"fromUserId"}
 * 
 * @Author: hzz
 * @Date: 2025/11/19
 * @Description: 会话列表元数据管理（方案B：只存ID+未读数）
 */
public interface ChatListService {
    
    /**
     * 更新会话列表元数据（新消息产生时调用）
     * 
     * @param userId 用户ID（接收方）
     * @param chatId 会话ID
     * @param msgId 最新消息ID
     * @param fromUserId 发送方ID
     * @param timestamp 消息时间戳
     */
    void updateChatListMetadata(String userId, String chatId, String msgId, String fromUserId, long timestamp);
    
    /**
     * 获取某个用户的所有会话列表元数据
     * 
     * @param userId 用户ID
     * @return Map<chatId, 元数据JSON>
     */
    Map<String, String> getAllChatListMetadata(String userId);
    
    /**
     * 获取单个会话的元数据
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     * @return 元数据JSON，如果不存在返回null
     */
    String getChatMetadata(String userId, String chatId);
    
    /**
     * 清零某个会话的未读数（用户已读时调用）
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     */
    void clearUnreadCount(String userId, String chatId);
    
    /**
     * 删除某个会话的元数据
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     */
    void deleteChatMetadata(String userId, String chatId);
    
    /**
     * 删除用户的所有会话列表数据
     * 
     * @param userId 用户ID
     */
    void deleteAllChatList(String userId);
}
