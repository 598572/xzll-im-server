package com.xzll.business.cluster.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.nacos.RocketMqConfig;
import com.xzll.business.service.impl.ServerAckSimpleRetryService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.RocketMQConcurrentlyConsumerListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/25
 * @Description: ServerAck重试消费者 - 使用独立的Consumer实例
 */
@Slf4j
@Component
public class ServerAckSimpleRetryConsumer implements InitializingBean {
    
    private static final String TAG = "[ServerAck重试消费者]";
    private static final String SERVER_ACK_RETRY_TOPIC = "SERVER_ACK_RETRY_TOPIC";
    private static final String CONSUMER_GROUP = "SERVER_ACK_RETRY_CONSUMER_GROUP";
    
    @Resource
    private RocketMqConfig rocketMqConfig;
    
    @Resource
    private ServerAckSimpleRetryService serverAckSimpleRetryService;
    
    private DefaultMQPushConsumer consumer;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 创建独立的Consumer实例，使用独立的Consumer Group
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(rocketMqConfig.getServerAddr());
        
        // 使用配置中心的消费者配置
        RocketMqConfig.ConsumerConfig consumerConfig = rocketMqConfig.getConsumer();
        if (consumerConfig != null) {
            consumer.setConsumeThreadMin(consumerConfig.getConsumeThreadMin());
            consumer.setConsumeThreadMax(consumerConfig.getConsumeThreadMax());
            consumer.setMaxReconsumeTimes(consumerConfig.getMaxReconsumeTimes());
            consumer.setConsumeMessageBatchMaxSize(consumerConfig.getConsumeMessageBatchMaxSize());
            consumer.setConsumeTimeout(consumerConfig.getConsumeTimeout());
        }
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        
        try {
            // 订阅ServerAck重试Topic
            consumer.subscribe(SERVER_ACK_RETRY_TOPIC, "*");
            log.info("{}订阅Topic成功: {}", TAG, SERVER_ACK_RETRY_TOPIC);
            
            // 注册消息监听器（并发消费模式）
            consumer.registerMessageListener(new RocketMQConcurrentlyConsumerListener(this::handleEvent));
            
            // 启动Consumer
            consumer.start();
            log.info("{}初始化完成，开始监听主题: {}, Consumer Group: {}", TAG, SERVER_ACK_RETRY_TOPIC, CONSUMER_GROUP);
            
        } catch (MQClientException e) {
            log.error("{}初始化失败", TAG, e);
            throw e;
        }
    }
    
    /**
     * 处理ServerAck重试事件
     */
    public void handleEvent(String topicName, ClusterEvent clusterEvent) {
        try {
            log.info("{}收到重试事件 - topic: {}, eventType: {}, createTime: {}", 
                TAG, topicName, clusterEvent.getClusterEventType(), clusterEvent.getCreateTime());
            
            if (clusterEvent.getClusterEventType() == null || 
                !clusterEvent.getClusterEventType().equals(ImConstant.ClusterEventTypeConstant.SERVER_ACK_RETRY)) {
                log.warn("{}忽略非ServerAck重试事件 - eventType: {}", TAG, clusterEvent.getClusterEventType());
                return;
            }
            
            // 解析重试事件
            ServerAckSimpleRetryService.ServerAckRetryEvent retryEvent = 
                JSONUtil.toBean(clusterEvent.getData(), ServerAckSimpleRetryService.ServerAckRetryEvent.class);
            
            if (retryEvent == null || retryEvent.getServerAckPush() == null) {
                log.error("{}重试事件数据为空或格式错误 - data: {}", TAG, clusterEvent.getData());
                return;
            }
            
            // 直接执行重试（延迟已由RocketMQ处理）  
            log.info("{}收到延迟重试消息，开始执行重试 - clientMsgId: {}, 第{}次重试", 
                TAG, retryEvent.getServerAckPush().getClientMsgId(), retryEvent.getRetryCount());
            
            serverAckSimpleRetryService.handleMqRetry(retryEvent);
            
        } catch (Exception e) {
            log.error("{}处理ServerAck重试事件失败 - topic: {}, error: {}", 
                TAG, topicName, e.getMessage(), e);
        }
    }
}
