package com.xzll.common.rocketmq;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 用户群组缓存已更新事件
 *
 * 使用场景：
 * 当 im-business 重建缓存成功后，发送此事件通知所有 im-connect
 * im-connect 收到事件后，检查本地是否有该用户的连接，如果有则更新分片
 */
@Data
@Accessors(chain = true)
public class UserGroupCacheUpdatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 重建后的群列表
     */
    private List<String> groupIds;

    /**
     * 重建时间戳
     */
    private Long timestamp;

    /**
     * 重建原因
     */
    private String reason;

    /**
     * 构造函数
     */
    public UserGroupCacheUpdatedEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 快速构建事件
     */
    public static UserGroupCacheUpdatedEvent of(String userId, List<String> groupIds, String reason) {
        return new UserGroupCacheUpdatedEvent()
                .setUserId(userId)
                .setGroupIds(groupIds)
                .setReason(reason);
    }
}
