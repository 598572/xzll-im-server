package com.xzll.business.cluster.mq;

import cn.hutool.json.JSONUtil;
import com.xzll.common.rocketmq.ClusterEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: RocketMQ生产者包装类，用于发送数据同步消息
 */
@Slf4j
@Component
public class RocketMqProducerWrap {

    @Resource
    private DefaultMQProducer defaultMQProducer;

    /**
     * 发送延迟消息，利用RocketMQ的延迟消息功能
     *
     * @param topic 主题
     * @param event 集群事件
     * @param balanceId 用于保证顺序的业务ID
     * @param delaySeconds 延迟秒数
     * @return 是否发送成功
     */
    public boolean sendDelayClusterEvent(String topic, ClusterEvent event, String balanceId, int delaySeconds) {
        if (null == event || StringUtils.isBlank(topic)) {
            return false;
        }
        try {
            String json = JSONUtil.toJsonStr(event);
            byte[] body = json.getBytes();
            Message message = new Message(topic, body);
            
            // 设置延迟级别（RocketMQ预设延迟级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h）
            int delayLevel = getDelayLevel(delaySeconds);
            if (delayLevel > 0) {
                message.setDelayTimeLevel(delayLevel);
            }
            
            // 发送延迟消息
            SendResult sendResult = defaultMQProducer.send(message, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                    String balanceId = (String) arg;
                    int index = balanceId.hashCode() % mqs.size();
                    return mqs.get(Math.abs(index));
                }
            }, balanceId);
            
            log.info("发送延迟mq消息_topic:{} balanceId:{} delaySeconds:{} delayLevel:{} 发送结果:{}", 
                topic, balanceId, delaySeconds, delayLevel, JSONUtil.toJsonStr(sendResult));
            return true;
        } catch (Exception e) {
            log.error("发送延迟mq发生异常，topic: {}, balanceId: {}, delaySeconds: {}", topic, balanceId, delaySeconds, e);
            return false;
        }
    }
    
    /**
     * 根据延迟秒数获取RocketMQ延迟级别
     * RocketMQ预设延迟级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    private int getDelayLevel(int delaySeconds) {
        if (delaySeconds <= 1) return 1;       // 1s
        if (delaySeconds <= 5) return 2;       // 5s
        if (delaySeconds <= 10) return 3;      // 10s
        if (delaySeconds <= 30) return 4;      // 30s
        if (delaySeconds <= 60) return 5;      // 1m
        if (delaySeconds <= 120) return 6;     // 2m
        if (delaySeconds <= 180) return 7;     // 3m
        if (delaySeconds <= 240) return 8;     // 4m
        if (delaySeconds <= 300) return 9;     // 5m
        if (delaySeconds <= 360) return 10;    // 6m
        if (delaySeconds <= 420) return 11;    // 7m
        if (delaySeconds <= 480) return 12;    // 8m
        if (delaySeconds <= 540) return 13;    // 9m
        if (delaySeconds <= 600) return 14;    // 10m
        if (delaySeconds <= 1200) return 15;   // 20m
        if (delaySeconds <= 1800) return 16;   // 30m
        if (delaySeconds <= 3600) return 17;   // 1h
        return 18; // 2h (最大延迟级别)
    }

    /**
     * 发送顺序消息，根据指定topic和balanceId(相同balanceId 将会进入同一个队列中)
     * 用于保证同一会话的消息顺序
     *
     * @param topic 主题
     * @param event 集群事件
     * @param balanceId 用于保证顺序的业务ID (这里是chatId) 根据chatId 实现局部有序
     * @return 是否发送成功
     */
    public boolean sendClusterEvent(String topic, ClusterEvent event, String balanceId) {
        if (null == event || StringUtils.isBlank(topic)) {
            return false;
        }
        try {
            String json = JSONUtil.toJsonStr(event);
            // 序列化为字节流
            byte[] body = json.getBytes();
            Message message = new Message(topic, body);

            // 发送顺序消息
            SendResult sendResult = defaultMQProducer.send(message, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                    String balanceId = (String) arg;
                    int index = balanceId.hashCode() % mqs.size();
                    return mqs.get(Math.abs(index));
                }
            }, balanceId);
            
            log.info("发送顺序mq消息_topic:{} balanceId:{} 发送结果:{}", topic, balanceId, JSONUtil.toJsonStr(sendResult));
            return true;
        } catch (Exception e) {
            log.error("发送顺序mq发生异常，topic: {}, balanceId: {}", topic, balanceId, e);
            return false;
        }
    }
} 