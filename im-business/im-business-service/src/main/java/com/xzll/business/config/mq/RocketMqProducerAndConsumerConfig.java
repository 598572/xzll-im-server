package com.xzll.business.config.mq;


import com.xzll.business.config.nacos.RocketMqConfig;
import com.xzll.common.rocketmq.RocketMqConsumerMessageHook;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;


import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description:
 */
@Component
public class RocketMqProducerAndConsumerConfig {

	@Resource
	private RocketMqConfig rocketMqConfig;

	@Bean
	public DefaultMQPushConsumer defaultMQPushConsumer() {
//		DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(groupName);
//		consumer.setNamesrvAddr(namesrvAddr);
//		consumer.setConsumeThreadMin(consumeThreadMin);
//		consumer.setConsumeThreadMax(consumeThreadMax);
//		consumer.registerMessageListener(mqMsgListener);
//		consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
//		// consumer.setMessageModel(MessageModel.CLUSTERING);
//		consumer.setConsumeMessageBatchMaxSize(consumeMessageBatchMaxSize);
//		try {
//			consumer.subscribe(topics, "MyTag");
//			consumer.start();
//		} catch (MQClientException e) {
//		}

		// 实例化消费者 ，启动和订阅不在这里，是在有业务方订阅时候，被动触发 订阅和启动消费者逻辑
		DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(rocketMqConfig.getConsumerGroupName());
		consumer.setNamesrvAddr(rocketMqConfig.getServerAddr());
		consumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerMessageHook());
		return consumer;
	}

}
