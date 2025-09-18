package com.xzll.business.service;

import java.util.Map;

/**
 * 未读消息数量管理服务
 * 使用Redis存储和管理用户在各个会话中的未读消息数量
 * 
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: Redis未读消息数量管理
 */
public interface UnreadCountService {

    /**
     * 增加用户在指定会话的未读消息数
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     * @param increment 增加的数量，默认为1
     */
    void incrementUnreadCount(String userId, String chatId, int increment);

    /**
     * 增加用户在指定会话的未读消息数（默认+1）
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     */
    void incrementUnreadCount(String userId, String chatId);

    /**
     * 清零用户在指定会话的未读消息数
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     */
    void clearUnreadCount(String userId, String chatId);

    /**
     * 设置用户在指定会话的未读消息数
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     * @param count 未读数量
     */
    void setUnreadCount(String userId, String chatId, int count);

    /**
     * 获取用户在指定会话的未读消息数
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     * @return 未读消息数量，如果不存在则返回0
     */
    int getUnreadCount(String userId, String chatId);

    /**
     * 批量获取用户在所有会话的未读消息数
     * 
     * @param userId 用户ID
     * @return Map<chatId, unreadCount>
     */
    Map<String, Integer> getAllUnreadCounts(String userId);

    /**
     * 删除用户在指定会话的未读消息记录
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     */
    void deleteUnreadCount(String userId, String chatId);

    /**
     * 获取用户总的未读消息数（所有会话未读数之和）
     * 
     * @param userId 用户ID
     * @return 总未读消息数
     */
    int getTotalUnreadCount(String userId);

    /**
     * 清理指定用户的损坏未读数据
     * 当出现类型转换错误时可以调用此方法进行数据清理
     * 
     * @param userId 用户ID
     */
    void cleanupCorruptedData(String userId);

    /**
     * 修复指定用户指定会话的数据类型问题
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     */
    void fixDataType(String userId, String chatId);
}
