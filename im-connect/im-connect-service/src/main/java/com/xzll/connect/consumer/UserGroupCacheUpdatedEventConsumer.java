package com.xzll.connect.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.UserGroupCacheUpdatedEvent;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.service.GroupServerMemberService;
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
 * @Description: 用户群组缓存已更新事件消费者（im-connect）
 *
 * 功能：
 * 1. 消费 im-business 发送的"缓存已更新"事件
 * 2. 检查本地是否有该用户的连接
 * 3. 如果有，重新查询缓存，更新分片
 * 4. 如果没有，忽略（用户不在本服务器）
 */
@Slf4j
@Component
public class UserGroupCacheUpdatedEventConsumer implements InitializingBean {

    private static final String TAG = "[缓存已更新事件消费者]_";
    private static final String UPDATED_TOPIC = ImConstant.TopicConstant.USER_GROUP_CACHE_UPDATED_TOPIC;
    private static final String CONSUMER_GROUP = ImConstant.ConsumerGroupConstant.USER_GROUP_CACHE_UPDATED_CONSUMER;

    @Resource
    private LocalChannelManager localChannelManager;

    @Resource
    private GroupServerMemberService groupServerMemberService;

    @Resource
    private com.xzll.connect.config.RocketMqConfig rocketMqConfig;

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
        consumer.subscribe(UPDATED_TOPIC, "*");

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
                        log.info("{}接收到缓存已更新事件 - msgId:{}, queueId:{}, offset:{}",
                            TAG, msg.getMsgId(), msg.getQueueId(), msg.getQueueOffset());

                        // 处理消息
                        processMessage(messageJson);

                    } catch (Exception e) {
                        log.error("{}处理消息失败 - msgId:{}", TAG, msg.getMsgId(), e);
                        // 不重试，避免死循环
                    }
                }

                // 消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        // 启动消费者
        consumer.start();
        log.info("{}用户群组缓存已更新事件消费者启动成功 - topic:{}, group:{}, namesrv:{}",
            TAG, UPDATED_TOPIC, CONSUMER_GROUP, rocketMqConfig.getServerAddr());
    }

    /**
     * 处理缓存已更新事件
     */
    private void processMessage(String messageJson) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析 ClusterEvent
            ClusterEvent clusterEvent = JSONUtil.toBean(messageJson, ClusterEvent.class);
            log.debug("{}解析 ClusterEvent 成功 - eventType:{}", TAG, clusterEvent.getClusterEventType());

            // 2. 解析缓存已更新事件
            UserGroupCacheUpdatedEvent event = JSONUtil.toBean(
                    clusterEvent.getData(),
                    UserGroupCacheUpdatedEvent.class
            );

            String userId = event.getUserId();
            List<String> groupIds = event.getGroupIds();
            String reason = event.getReason();

            log.info("{}【步骤1-解析成功】userId:{}, count:{}, reason:{}",
                    TAG, userId, groupIds != null ? groupIds.size() : 0, reason);

            // 3. 检查本地是否有该用户的连接
            io.netty.channel.Channel channel = localChannelManager.getChannelByUserId(userId);

            if (channel == null || !channel.isActive()) {
                log.debug("{}【步骤2-检查连接】用户不在本服务器 - userId:{}", TAG, userId);
                return;
            }

            log.info("{}【步骤2-检查连接】用户在本服务器，开始更新分片 - userId:{}", TAG, userId);

            // 4. 获取当前服务器信息
            String serverIp = groupServerMemberService.getServerIp();

            if (serverIp == null) {
                log.warn("{}【步骤3-获取服务器】无法获取本服务器IP - userId:{}", TAG, userId);
                return;
            }

            // 5. 重新查询缓存（此时缓存已经重建好了）
            List<String> cachedGroupIds = groupServerMemberService.getUserGroupIdsFromCache(userId);

            if (cachedGroupIds == null || cachedGroupIds.isEmpty()) {
                log.warn("{}【步骤3-查询缓存】缓存仍未命中 - userId:{}", TAG, userId);
                return;
            }

            log.info("{}【步骤3-查询缓存】查询成功 - userId:{}, count:{}", TAG, userId, cachedGroupIds.size());

            // 6. 更新分片信息
            groupServerMemberService.batchUpdateGroupServerMember(
                    cachedGroupIds,
                    userId,
                    serverIp,
                    true
            );

            long costTime = System.currentTimeMillis() - startTime;

            log.info("{}【步骤4-更新分片】分片更新成功 - userId:{}, count:{}, cost:{}ms",
                    TAG, userId, cachedGroupIds.size(), costTime);

        } catch (Exception e) {
            log.error("{}处理缓存已更新事件失败", TAG, e);
        }
    }
}
