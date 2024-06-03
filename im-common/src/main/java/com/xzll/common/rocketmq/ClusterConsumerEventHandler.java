package com.xzll.common.rocketmq;

import cn.hutool.json.JSONUtil;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author: hzz
 * @Date: 2023/3/1 09:27:15
 * @Description: 消费模板类 在 RocketMQMsgListener类中调用
 */
public class ClusterConsumerEventHandler {
	private static final Logger logger = LoggerFactory.getLogger(ClusterConsumerEventHandler.class);


	private RocketMQClusterEventListener listener;


	public ClusterConsumerEventHandler(RocketMQClusterEventListener listener) {
		this.listener = listener;
	}

	/**
	 * 消费消息模板方法（定义一些消费时候公共逻辑）
	 *
	 * @param message
	 * @return
	 */
	public ConsumeConcurrentlyStatus messageHandle(MessageExt message) {
		String body = null;
		ClusterEvent clusterEvent;
		try {
			body = new String(message.getBody());
			clusterEvent = JSONUtil.toBean(body, ClusterEvent.class);
		} catch (Exception e) {
			logger.error("解码事件对象异常, topic={}, tags={}, queueId={}, body={}", message.getTopic(), message.getTags(), message.getQueueId(), body);
			return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
		}

		if (null == clusterEvent) {
			logger.error("事件对象null, topic={}, tags={}, queueId={}, body={}", message.getTopic(), message.getTags(), message.getQueueId(), body);
			return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
		}
		logger.info("事件处理开始执行, topic={}, tags={}, queueId={}, bornTimestamp={}, storeTimestamp={}, body={}",
				message.getTopic(), message.getTags(), message.getQueueId(), message.getBornTimestamp(), message.getStoreTimestamp(), body);
		try {
			listener.handleEvent(message.getTopic(), clusterEvent);
		} catch (Exception e) {
			logger.error("事件处理失败, topic={}, tags={}, queueId={}, body={}", message.getTopic(), message.getTags(), message.getQueueId(), body, e);
			return ConsumeConcurrentlyStatus.RECONSUME_LATER;
		}

		return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
	}
}
