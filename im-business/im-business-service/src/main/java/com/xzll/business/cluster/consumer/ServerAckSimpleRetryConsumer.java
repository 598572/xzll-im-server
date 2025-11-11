package com.xzll.business.cluster.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.mq.RocketMqConsumerWrap;

import com.xzll.business.service.impl.ServerAckSimpleRetryService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/25
 * @Description: ServerAck简化重试消费者 - 纯MQ方案
 */
@Slf4j
@Component
public class ServerAckSimpleRetryConsumer implements RocketMQClusterEventListener, InitializingBean {
    
    private static final String TAG = "[ServerAck重试消费者]";
    private static final String SERVER_ACK_RETRY_TOPIC = "SERVER_ACK_RETRY_TOPIC";
    
    @Resource
    private RocketMqConsumerWrap consumer;
    
    @Resource
    private ServerAckSimpleRetryService serverAckSimpleRetryService;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        java.util.List<String> topics = new java.util.ArrayList<>();
        topics.add(SERVER_ACK_RETRY_TOPIC);
        consumer.subscribeByConcurrentlyConsumer(topics, this);
        log.info("{}初始化完成，开始监听主题: {}", TAG, SERVER_ACK_RETRY_TOPIC);
    }
    
    @Override
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
