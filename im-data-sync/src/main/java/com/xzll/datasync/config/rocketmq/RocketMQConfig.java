package com.xzll.datasync.config.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置类
 * 提供 DefaultMQPushConsumer Bean
 * 
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:im-data-sync-consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.consumer.consume-thread-min:1}")
    private int consumeThreadMin;

    @Value("${rocketmq.consumer.consume-thread-max:20}")
    private int consumeThreadMax;

    @Value("${rocketmq.consumer.max-reconsume-times:3}")
    private int maxReconsumeTimes;

    @Value("${rocketmq.consumer.consume-timeout:30000}")
    private int consumeTimeout;

    @Value("${rocketmq.consumer.consume-message-batch-max-size:100}")
    private int consumeMessageBatchMaxSize;

    @Value("${rocketmq.consumer.consume-from-where:CONSUME_FROM_LAST_OFFSET}")
    private String consumeFromWhere;

    /**
     * 创建 DefaultMQPushConsumer Bean
     */
    @Bean
    public DefaultMQPushConsumer defaultMQPushConsumer() {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        
        // 设置命名服务器地址
        consumer.setNamesrvAddr(nameServer);
        
        // 设置消费线程数
        consumer.setConsumeThreadMin(consumeThreadMin);
        consumer.setConsumeThreadMax(consumeThreadMax);
        
        // 设置重试次数
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
        
        // 设置消费超时
        consumer.setConsumeTimeout(consumeTimeout);
        
        // 设置批量消费大小
        consumer.setConsumeMessageBatchMaxSize(consumeMessageBatchMaxSize);
        
        // 设置消费起始位置
        try {
            ConsumeFromWhere fromWhere = ConsumeFromWhere.valueOf(consumeFromWhere);
            consumer.setConsumeFromWhere(fromWhere);
        } catch (IllegalArgumentException e) {
            log.warn("无效的消费起始位置: {}, 使用默认值: CONSUME_FROM_LAST_OFFSET", consumeFromWhere);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        }
        
        log.info("RocketMQ 消费者配置完成 - nameServer: {}, group: {}, batchSize: {}, threads: {}-{}", 
                nameServer, consumerGroup, consumeMessageBatchMaxSize, consumeThreadMin, consumeThreadMax);
        
        return consumer;
    }
} 