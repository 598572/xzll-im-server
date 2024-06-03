package com.xzll.common.rocketmq;


/**
 * @Author: hzz
 * @Date: 2023/3/1 09:29:10
 * @Description: 所有需要监听消息的地方 实现该接口 并自行定义感兴趣的topic
 */
public interface RocketMQClusterEventListener {
    void handleEvent(final String topicName, final ClusterEvent event);
}
