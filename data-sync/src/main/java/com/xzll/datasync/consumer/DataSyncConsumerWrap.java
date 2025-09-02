package com.xzll.datasync.consumer;

import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import com.xzll.common.rocketmq.RocketMQConcurrentlyConsumerListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * Data Sync模块的RocketMQ消费者包装类
 * 基于现有的RocketMQ架构实现
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Component
public class DataSyncConsumerWrap {

    @Resource
    private DefaultMQPushConsumer defaultMQPushConsumer;

    /**
     * 此方法将并发消费
     *
     * @param topics               订阅的topic列表
     * @param clusterEventListener 消息处理逻辑的实现
     */
    public void subscribeByConcurrentlyConsumer(List<String> topics, RocketMQClusterEventListener clusterEventListener) {
        // 订阅topic
        if (!CollectionUtils.isEmpty(topics)) {
            for (String topic : topics) {
                try {
                    this.defaultMQPushConsumer.subscribe(topic, "*");
                    log.info("成功订阅topic: {}", topic);
                } catch (MQClientException e) {
                    log.error("订阅topic异常, topic={}", topic, e);
                }
            }
        }
        // 注册消息监听者 并发消费模式
        defaultMQPushConsumer.registerMessageListener(new RocketMQConcurrentlyConsumerListener(clusterEventListener));

        //启动consumer
        try {
            defaultMQPushConsumer.start();
            log.info("DataSyncConsumerWrap启动成功");
        } catch (MQClientException e) {
            log.error("启动RocketMq consumer异常 ", e);
        }
    }
}
