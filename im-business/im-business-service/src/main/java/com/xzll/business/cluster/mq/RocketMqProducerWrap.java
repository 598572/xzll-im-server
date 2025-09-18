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

import javax.annotation.Resource;
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