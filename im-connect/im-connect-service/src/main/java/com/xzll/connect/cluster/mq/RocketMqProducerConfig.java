package com.xzll.connect.cluster.mq;


import com.xzll.common.rocketmq.RocketMqSendMessageHook;
import com.xzll.connect.config.RocketMqConfig;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description: 生产者配置
 */
@Component
public class RocketMqProducerConfig {

	@Resource
	private RocketMqConfig rocketMqConfig;

	/**
	 * 暂时最简单的配置 后期改成集群配置， redis mysql等也是一样
	 *
	 * @return
	 */
	@Bean
	public DefaultMQProducer defaultMQProducer() {
		// 实例化一个生产者来产生消息
		DefaultMQProducer producer = new DefaultMQProducer(rocketMqConfig.getProducerGroupName());
		producer.setNamesrvAddr(rocketMqConfig.getServerAddr());
		producer.setSendMsgTimeout(rocketMqConfig.getSendTimeOut());
		//设置钩子
		producer.getDefaultMQProducerImpl().registerSendMessageHook(new RocketMqSendMessageHook());
		// 启动生产者
		try {
			producer.start();
		} catch (MQClientException e) {
		}
		return producer;
	}

}
