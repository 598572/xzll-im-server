package com.xzll.common.rocketmq;

import cn.hutool.core.collection.CollectionUtil;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @Author: hzz
 *
 * @Date: 2023/2/28 18:13:17
 * @Description: 顺序消息监听，该类由创建consumer实例时候  通过new的方式注册到consumer上去
 */
public class RocketMQOrderConsumerListener implements MessageListenerOrderly {
	private static final Logger logger = LoggerFactory.getLogger(RocketMQOrderConsumerListener.class);


	private ClusterConsumerEventHandler clusterConsumerEventHandler;

	//该实例的对象创建是在   在consumer实例化后 注册该监听器的时候
	public RocketMQOrderConsumerListener(RocketMQClusterEventListener clusterEventListener) {
		this.clusterConsumerEventHandler = new ClusterConsumerEventHandler(clusterEventListener);
	}

	@Override
	public ConsumeOrderlyStatus consumeMessage(List<MessageExt> messages, ConsumeOrderlyContext consumeOrderlyContext) {
		if (CollectionUtil.isEmpty(messages)) {
			return ConsumeOrderlyStatus.SUCCESS;
		}
		//2. 进行消息处理
		for (MessageExt message : messages) {
			long startTime = System.currentTimeMillis();
			try {
				ConsumeOrderlyStatus status = clusterConsumerEventHandler.orderMessageHandle(message);
				if (!status.equals(ConsumeOrderlyStatus.SUCCESS)) {
					return status;
				}

			} finally {
				long usedTime = System.currentTimeMillis() - startTime;
				logger.info("rocketMQ_消息处理耗时, usedTime={}, topic={}, tags={}, queueId={}", usedTime, message.getTopic(), message.getTags(), message.getQueueId());
			}
		}
		// 如果没有return success ，consumer会重新消费该消息，直到return success
		return ConsumeOrderlyStatus.SUCCESS;
	}
}
