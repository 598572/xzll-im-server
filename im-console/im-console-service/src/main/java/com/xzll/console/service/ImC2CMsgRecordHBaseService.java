package com.xzll.console.service;

import com.xzll.console.entity.ImC2CMsgRecord;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息HBase存储服务接口
 */
public interface ImC2CMsgRecordHBaseService {

    /**
     * 查询所有消息记录
     * @return 消息记录列表
     */
    List<ImC2CMsgRecord> getAllMessages();

    /**
     * 根据条件查询消息记录
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param chatId 会话ID
     * @return 消息记录列表
     */
    List<ImC2CMsgRecord> getMessagesByCondition(String fromUserId, String toUserId, String chatId);

    /**
     * 根据会话ID查询消息记录
     * @param chatId 会话ID
     * @param limit 限制数量
     * @return 消息记录列表
     */
    List<ImC2CMsgRecord> getMessagesByChatId(String chatId, int limit);
} 