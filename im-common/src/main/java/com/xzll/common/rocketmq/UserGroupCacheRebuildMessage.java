package com.xzll.common.rocketmq;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 用户群组缓存重建消息
 *
 * 使用场景：
 * 当 im-connect 发现 user:groups:{userId} 缓存未命中时，
 * 发送此消息到 MQ，由 im-business 消费并重建缓存
 */
@Data
@Accessors(chain = true)
public class UserGroupCacheRebuildMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 触发时间戳
     */
    private Long timestamp;

    /**
     * 触发原因
     * FIRST_LOGIN - 首次登录
     * CACHE_EXPIRED - 缓存过期
     * CACHE_MISS - 缓存未命中（其他原因）
     */
    private String reason;

    /**
     * 构造函数
     */
    public UserGroupCacheRebuildMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 快速构建消息
     */
    public static UserGroupCacheRebuildMessage of(String userId, String reason) {
        return new UserGroupCacheRebuildMessage()
                .setUserId(userId)
                .setReason(reason);
    }
}
