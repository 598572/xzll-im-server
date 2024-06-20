package com.xzll.connect.config;

import com.xzll.common.rocketmq.RocketMqConsumerMessageHook;
import com.xzll.common.rocketmq.RocketMqSendMessageHook;
import lombok.Getter;
import lombok.Setter;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


/**
 * @Author: hzz
 * @Date: 2024/6/1 11:01:20
 * @Description: rocketMq配置
 */
@Setter
@Getter
@Component
@RefreshScope
@ConfigurationProperties(prefix = "im.rocket")
public class RocketMqConfig {

    //RocketMQ服务地址
    private String serverAddr;

    // 生产者配置
    private ProducerConfig producer;

    // 消费者配置
    private ConsumerConfig consumer;


    @Setter
    @Getter
    public static class ProducerConfig {
        // 生产者组名称
        private String producerGroupName;
        // 最大消息大小（单位：字节）
        private int maxMessageSize;
        // 发送消息的超时时间（单位：毫秒）
        private int sendMsgTimeout;
        // 消息发送失败的重试次数
        private int retryTimesWhenSendFailed;
    }

    @Setter
    @Getter
    public static class ConsumerConfig {
        // 消费者组名称
        private String consumerGroupName;
        // 消费线程池最小线程数
        private int consumeThreadMin;
        // 消费线程池最大线程数
        private int consumeThreadMax;
        // 最大重试消费次数
        private int maxReconsumeTimes;
        // 一次消费的消息数量
        private int consumeMessageBatchMaxSize;
        // 消息消费超时时间（单位：分钟）
        private int consumeTimeout;
    }



    @Bean
    public DefaultMQProducer defaultMQProducer() {
        RocketMqConfig.ProducerConfig producerConfig = this.getProducer();
        DefaultMQProducer producer = new DefaultMQProducer(producerConfig.getProducerGroupName());
        producer.setNamesrvAddr(this.getServerAddr());
        producer.setMaxMessageSize(producerConfig.getMaxMessageSize());
        producer.setSendMsgTimeout(producerConfig.getSendMsgTimeout());
        producer.setRetryTimesWhenSendFailed(producerConfig.getRetryTimesWhenSendFailed());
        //设置钩子
        producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMqSendMessageHook());
        // 启动生产者
        try {
            producer.start();
        } catch (MQClientException e) {
        }
        return producer;
    }

    @Bean
    public DefaultMQPushConsumer defaultMQPushConsumer() {
        RocketMqConfig.ConsumerConfig consumerConfig = this.getConsumer();
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerConfig.getConsumerGroupName());
        consumer.setNamesrvAddr(this.getServerAddr());
        consumer.setConsumeThreadMin(consumerConfig.getConsumeThreadMin());
        consumer.setConsumeThreadMax(consumerConfig.getConsumeThreadMax());
        consumer.setMaxReconsumeTimes(consumerConfig.getMaxReconsumeTimes());
        consumer.setConsumeMessageBatchMaxSize(consumerConfig.getConsumeMessageBatchMaxSize());
        consumer.setConsumeTimeout(consumerConfig.getConsumeTimeout());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        consumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerMessageHook());
        return consumer;
    }
}
