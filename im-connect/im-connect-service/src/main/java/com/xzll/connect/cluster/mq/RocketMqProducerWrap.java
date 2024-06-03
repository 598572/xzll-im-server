package com.xzll.connect.cluster.mq;


import cn.hutool.json.JSONUtil;
import com.xzll.common.rocketmq.ClusterEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description: rocketMq 生产者包装类
 */
@Slf4j
@Component
public class RocketMqProducerWrap {


    @Resource
    private DefaultMQProducer defaultMQProducer;


    /**
     * 发送消息，根据指定topic和事件数据
     *
     * @param topic
     * @param event
     * @return
     */
    public boolean sendClusterEvent(String topic, ClusterEvent event) {
        if (null == event || StringUtils.isBlank(topic)) {
            return false;
        }
        try {
            String json = JSONUtil.toJsonStr(event);
            //序列化为字节流
            byte[] body = json.getBytes();
            Message message = new Message(topic, body);
            SendResult sendResult = null;
            if (StringUtils.isBlank(event.getBalanceId())) {
                sendResult = defaultMQProducer.send(message);
            } else {
                //todo 后期扩展带balanceId的功能
            }
            log.info("发送mq消息_topic:{}发送结果:{}", topic, JSONUtil.toJsonStr(sendResult));
        } catch (Exception e) {
            log.error("发送mq发生异常", e);
            return false;
        }
        return true;
    }


}
