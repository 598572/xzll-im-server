package com.xzll.business.config.mq;


import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import com.xzll.common.rocketmq.RocketMQConcurrentlyConsumerListener;
import com.xzll.common.rocketmq.RocketMQOrderConsumerListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description: 消费包装类 目前提供 ：并发消费、顺序消费【两种模式】
 * 每个不同的监听实现（在RocketMQClusterEventListener对应的实现类中），通过传入topics进行对这些topic的监听，进而消费数据,这样可以将消费与业务逻辑：解耦
 */
@Slf4j
@Component
public class RocketMqConsumerWrap {


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
        } catch (MQClientException e) {
            log.error("启动RocketMq consumer异常 ", e);
        }
    }


    /**
     * 此方法将顺序消费
     *
     * @param topics               订阅的topic列表
     * @param clusterEventListener 消息处理逻辑的实现
     */
    public void subscribeByOrderConsumer(List<String> topics, RocketMQClusterEventListener clusterEventListener) {
        // 订阅topic
        if (!CollectionUtils.isEmpty(topics)) {
            for (String topic : topics) {
                try {
                    this.defaultMQPushConsumer.subscribe(topic, "*");
                } catch (MQClientException e) {
                    log.error("订阅topic异常, topic={}", topic, e);
                }
            }
        }
        // 注册顺序消息监听者
        defaultMQPushConsumer.registerMessageListener(new RocketMQOrderConsumerListener(clusterEventListener));

        // 启动consumer
        try {
            defaultMQPushConsumer.start();
        } catch (MQClientException e) {
            log.error("启动RocketMq consumer异常 ", e);
        }
    }

}
