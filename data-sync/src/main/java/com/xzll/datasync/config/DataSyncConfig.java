package com.xzll.datasync.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Data Sync模块的RocketMQ配置
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Configuration
@Slf4j
public class DataSyncConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:im-data-sync-producer-group}")
    private String producerGroup;

    @Value("${rocketmq.consumer.group:im-data-sync-consumer-group}")
    private String consumerGroup;

    private DefaultMQProducer producer;

    /**
     * 配置DefaultMQProducer
     */
    @Bean
    public DefaultMQProducer defaultMQProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(3000);
        return producer;
    }

    /**
     * 配置DefaultMQPushConsumer
     */
    @Bean
    public DefaultMQPushConsumer defaultMQPushConsumer() {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(4);
        consumer.setMaxReconsumeTimes(3);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeTimeout(30000);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        return consumer;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("初始化DefaultMQProducer，nameServer: {}, producerGroup: {}", nameServer, producerGroup);
            // 直接创建producer实例，避免循环依赖
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer);
            producer.setSendMsgTimeout(3000);
            producer.start();
            log.info("DefaultMQProducer启动成功");
        } catch (Exception e) {
            log.error("DefaultMQProducer启动失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (producer != null) {
                producer.shutdown();
                log.info("DefaultMQProducer已关闭");
            }
        } catch (Exception e) {
            log.error("关闭DefaultMQProducer失败", e);
        }
    }
}
