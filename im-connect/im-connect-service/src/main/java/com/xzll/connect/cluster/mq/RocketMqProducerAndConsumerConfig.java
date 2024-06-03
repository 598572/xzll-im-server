package com.xzll.connect.cluster.mq;


import com.xzll.common.rocketmq.RocketMqConsumerMessageHook;
import com.xzll.common.rocketmq.RocketMqSendMessageHook;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description:
 */
@Component
public class RocketMqProducerAndConsumerConfig {


	@Bean
	public DefaultMQProducer defaultMQProducer() {
		// 实例化一个生产者来产生消息
		DefaultMQProducer producer = new DefaultMQProducer("ExampleProducerGroup");
		producer.setNamesrvAddr("172.30.128.65:9876");
		producer.setSendMsgTimeout(60000);
		//设置钩子
		producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMqSendMessageHook());
		// 启动生产者
		try {
			producer.start();
		} catch (MQClientException e) {
		}
		return producer;
	}


//	@Value("${rocketmq.producer.groupName}")
//	private String groupName;
//	@Value("${rocketmq.producer.namesrvAddr}")
//	private String namesrvAddr;
//	@Value("${rocketmq.producer.maxMessageSize}")
//	private Integer maxMessageSize;
//	@Value("${rocketmq.producer.sendMsgTimeout}")
//	private Integer sendMsgTimeout;
//	@Value("${rocketmq.producer.retryTimesWhenSendFailed}")
//	private Integer retryTimesWhenSendFailed;

//	@Bean
//	public DefaultMQProducer defaultMQProducer() {
//		DefaultMQProducer producer = new DefaultMQProducer(this.groupName);
//		producer.setNamesrvAddr(this.namesrvAddr);
//		producer.setMaxMessageSize(this.maxMessageSize);
//		producer.setSendMsgTimeout(this.sendMsgTimeout);
//		producer.setRetryTimesWhenSendFailed(this.retryTimesWhenSendFailed);
//		try {
//			producer.start();
//		} catch (MQClientException e) {
//			e.printStackTrace();
//		}
//		return producer;
//	}






	//--------------consumer





//	@Value("${rocketmq.consumer.namesrvAddr}")
//	private String namesrvAddr;
//	@Value("${rocketmq.consumer.groupName}")
//	private String groupName;
//	@Value("${rocketmq.consumer.consumeThreadMin}")
//	private int consumeThreadMin;
//	@Value("${rocketmq.consumer.consumeThreadMax}")
//	private int consumeThreadMax;
//	@Value("${rocketmq.consumer.topics}")
//	private String topics;
//	@Value("${rocketmq.consumer.consumeMessageBatchMaxSize}")
//	private int consumeMessageBatchMaxSize;


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
		DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("ExampleConsumer");
		consumer.setNamesrvAddr("172.30.128.65:9876");
		consumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new RocketMqConsumerMessageHook());
		return consumer;
	}

}
