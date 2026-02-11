package com.xzll.connect.cluster.mq;

import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.UserGroupCacheRebuildMessage;
import com.xzll.connect.config.RocketMqConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 用户群组缓存 MQ 提供者
 *
 * 功能：
 * 1. 发送缓存重建消息到 MQ（im-connect 使用）
 * 2. 由 im-business 消费并重建缓存
 */
@Slf4j
@Component
public class UserGroupCacheMqProvider {

    private static final String TAG = "[用户群组缓存MQ提供者]_";

    private static final String CACHE_REBUILD_TOPIC = ImConstant.TopicConstant.USER_GROUP_CACHE_REBUILD_TOPIC;

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    /**
     * 发送缓存重建消息
     *
     * @param userId 用户ID
     * @param reason 触发原因（FIRST_LOGIN, CACHE_EXPIRED, CACHE_MISS）
     * @return 发送结果
     */
    public boolean sendCacheRebuildMessage(String userId, String reason) {
        boolean result = false;
        try {
            // 构建 MQ 消息
            UserGroupCacheRebuildMessage message = UserGroupCacheRebuildMessage.of(userId, reason);

            // 包装为 ClusterEvent
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(message));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.USER_GROUP_CACHE_REBUILD);

            // 发送到 MQ
            result = rocketMqProducerWrap.sendClusterEvent(CACHE_REBUILD_TOPIC, clusterEvent, userId);

            log.info("{}发送缓存重建消息 - userId:{}, reason:{}, result:{}",
                    TAG, userId, reason, result);

        } catch (Exception e) {
            log.error("{}发送缓存重建消息失败 - userId:{}, reason:{}",
                    TAG, userId, reason, e);
        }
        return result;
    }
}
