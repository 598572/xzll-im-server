package com.xzll.business.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.cluster.mq.RocketMqProducerWrap;
import com.xzll.business.config.nacos.RocketMqConfig;
import com.xzll.business.service.UserGroupCacheService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.UserGroupCacheRebuildMessage;
import com.xzll.common.rocketmq.UserGroupCacheUpdatedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 用户群组缓存重建消费者（im-business）
 *
 * 功能：
 * 1. 消费 im-connect 发送的缓存重建消息
 * 2. 查询数据库，获取用户的群列表
 * 3. 写入 Redis 缓存
 * 4. 发送"缓存已更新"事件（广播到所有 im-connect）
 */
@Slf4j
@Component
public class UserGroupCacheRebuildConsumer implements InitializingBean {

    private static final String TAG = "[用户群组缓存重建消费者]_";
    private static final String REBUILD_TOPIC = ImConstant.TopicConstant.USER_GROUP_CACHE_REBUILD_TOPIC;
    private static final String CONSUMER_GROUP = ImConstant.ConsumerGroupConstant.USER_GROUP_CACHE_REBUILD_CONSUMER;
    private static final String UPDATED_TOPIC = ImConstant.TopicConstant.USER_GROUP_CACHE_UPDATED_TOPIC;

    @Resource
    private UserGroupCacheService userGroupCacheService;

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    @Resource
    private RocketMqConfig rocketMqConfig;

    private DefaultMQPushConsumer consumer;

    @Override
    public void afterPropertiesSet() throws MQClientException {
        // 初始化消费者
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);

        // 从Nacos配置中心读取NameServer地址
        consumer.setNamesrvAddr(rocketMqConfig.getServerAddr());
        log.info("{}从配置中心读取NameServer地址: {}", TAG, rocketMqConfig.getServerAddr());

        // 设置消费起始位置
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 订阅Topic
        consumer.subscribe(REBUILD_TOPIC, "*");

        // 注册消息监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> msgs,
                    ConsumeConcurrentlyContext context) {

                for (MessageExt msg : msgs) {
                    try {
                        // 解析消息
                        String messageJson = new String(msg.getBody(), "UTF-8");
                        log.info("{}接收到缓存重建消息 - msgId:{}, queueId:{}, offset:{}",
                            TAG, msg.getMsgId(), msg.getQueueId(), msg.getQueueOffset());

                        // 处理消息
                        processMessage(messageJson);

                    } catch (Exception e) {
                        log.error("{}处理消息失败 - msgId:{}", TAG, msg.getMsgId(), e);
                        // 返回稍后重试
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                // 消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        // 启动消费者
        consumer.start();
        log.info("{}用户群组缓存重建消费者启动成功 - topic:{}, group:{}, namesrv:{}",
            TAG, REBUILD_TOPIC, CONSUMER_GROUP, rocketMqConfig.getServerAddr());
    }

    /**
     * 处理缓存重建消息
     */
    private void processMessage(String messageJson) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析 ClusterEvent
            ClusterEvent clusterEvent = JSONUtil.toBean(messageJson, ClusterEvent.class);
            log.debug("{}解析 ClusterEvent 成功 - eventType:{}", TAG, clusterEvent.getClusterEventType());

            // 2. 解析缓存重建消息
            UserGroupCacheRebuildMessage rebuildMessage = JSONUtil.toBean(
                    clusterEvent.getData(),
                    UserGroupCacheRebuildMessage.class
            );

            String userId = rebuildMessage.getUserId();
            String reason = rebuildMessage.getReason();

            log.info("{}【步骤1-解析成功】userId:{}, reason:{}", TAG, userId, reason);

            // 3. 查询数据库，获取用户的群列表
            List<String> groupIds = userGroupCacheService.refreshUserGroupsCache(userId);

            long costTime = System.currentTimeMillis() - startTime;

            if (groupIds == null || groupIds.isEmpty()) {
                log.info("{}【步骤2-数据库查询】用户未加入任何群 - userId:{}, cost:{}ms",
                        TAG, userId, costTime);

                // 即使没有群，也发送"缓存已更新"事件（清除旧缓存）
                sendCacheUpdatedEvent(userId, new java.util.ArrayList<>(), reason);
                return;
            }

            log.info("{}【步骤2-数据库查询】查询成功 - userId:{}, count:{}, cost:{}ms",
                    TAG, userId, groupIds.size(), costTime);

            // 4. 发送"缓存已更新"事件（广播到所有 im-connect）
            sendCacheUpdatedEvent(userId, groupIds, reason);

            log.info("{}【步骤3-发送通知】缓存重建完成 - userId:{}, count:{}, totalCost:{}ms",
                    TAG, userId, groupIds.size(), System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("{}处理缓存重建消息失败", TAG, e);
            throw e;
        }
    }

    /**
     * 发送"缓存已更新"事件
     */
    private void sendCacheUpdatedEvent(String userId, List<String> groupIds, String reason) {
        try {
            // 构建事件
            UserGroupCacheUpdatedEvent event = UserGroupCacheUpdatedEvent.of(userId, groupIds, reason);

            // 包装为 ClusterEvent
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(event));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.USER_GROUP_CACHE_UPDATED);

            // 发送到 MQ（广播到所有 im-connect）
            boolean result = rocketMqProducerWrap.sendClusterEvent(UPDATED_TOPIC, clusterEvent, userId);

            log.info("{}发送缓存已更新事件 - userId:{}, count:{}, result:{}",
                    TAG, userId, groupIds.size(), result);

        } catch (Exception e) {
            log.error("{}发送缓存已更新事件失败 - userId:{}", TAG, userId, e);
        }
    }
}
