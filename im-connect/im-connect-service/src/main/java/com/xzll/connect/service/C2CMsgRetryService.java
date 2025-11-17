package com.xzll.connect.service;

import com.xzll.common.pojo.request.C2CSendMsgAO;

/**
 * C2C消息重试服务接口
 * 使用Redis ZSet实现延迟队列，定时任务扫描到期消息
 *
 * ps: 此机制是消息可靠性重要一环！提供了服务端消息重推的机制
 *
 * @Author: hzz
 * @Date: 2025-11-14
 */
public interface C2CMsgRetryService {

    /**
     * 添加消息到延迟队列（等待客户端ACK）
     * 使用Lua脚本保证原子性：同时添加到ZSet和Hash
     * 在C2CMsgSendProtoStrategyImpl.exchange中调用
     *
     * @param packet 消息数据
     */
    void addToRetryQueue(C2CSendMsgAO packet);

    /**
     * 从延迟队列删除消息（收到客户端ACK时）
     * 使用Lua脚本保证原子性：同时从ZSet和Hash删除
     *
     * @param clientMsgId 客户端消息ID
     */
    void removeFromRetryQueue(String clientMsgId);
}
