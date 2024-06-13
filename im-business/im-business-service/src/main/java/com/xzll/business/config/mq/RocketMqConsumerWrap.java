package com.xzll.business.config.mq;


import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import com.xzll.common.rocketmq.RocketMQConsumerListener;
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
 * @Description: 消费包装类
 */
@Slf4j
@Component
public class RocketMqConsumerWrap {


	@Resource
	private DefaultMQPushConsumer defaultMQPushConsumer;


	/**
	 * 每个不同的监听实现（RocketMQClusterEventListener实现类）中，通过传入topics进行对这些topic的监听，进而消费数据,以及解耦/分开 消费逻辑
	 *
	 * @param topics
	 * @param clusterEventListener
	 */
	public void subscribe(List<String> topics, RocketMQClusterEventListener clusterEventListener) {
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
		// 注册消息监听者
		defaultMQPushConsumer.registerMessageListener(new RocketMQConsumerListener(clusterEventListener));

		/**
		 *  启动consumer
		 */
		try {
			defaultMQPushConsumer.start();
		} catch (MQClientException e) {
			log.error("启动RocketMq consumer异常 ", e);
		}
	}



}
