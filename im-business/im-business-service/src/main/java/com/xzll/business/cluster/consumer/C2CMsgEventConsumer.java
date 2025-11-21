package com.xzll.business.cluster.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.nacos.RocketMqConfig;
import com.xzll.business.handler.c2c.C2CClientWithdrawMsgHandler;
import com.xzll.business.handler.c2c.C2CSendMsgHandler;
import com.xzll.business.handler.c2c.C2CClientReceivedAckMsgHandler;
import com.xzll.business.handler.c2c.C2COffLineMsgHandler;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.rocketmq.RocketMQOrderConsumerListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2023/3/1 11:25:10
 * @Description: 单聊消息消费方 - 使用独立的Consumer实例
 * 此消费者为顺序消费（同一个消息id会进入同一个队列，不同消息id之间不影响）
 * 避免乱序导致异常（如：某消息id=1的消息 在更新时还没插入）
 */
@Slf4j
@Component
public class C2CMsgEventConsumer implements InitializingBean {

    private static final String TAG = "[C2C消息消费者]";
    private static final String C2C_TOPIC = ImConstant.TopicConstant.XZLL_C2CMSG_TOPIC;
    private static final String CONSUMER_GROUP = "C2C_MSG_CONSUMER_GROUP";

    @Resource
    private RocketMqConfig rocketMqConfig;
    
    @Resource
    private C2CSendMsgHandler c2CSendMsgHandler;
    @Resource
    private C2COffLineMsgHandler c2COffLineMsgHandler;
    @Resource
    private C2CClientReceivedAckMsgHandler c2CClientReceivedAckMsgHandler;
    @Resource
    private C2CClientWithdrawMsgHandler c2CClientWithdrawMsgHandler;
    
    private DefaultMQPushConsumer consumer;

    /**
     * 初始化独立的Consumer实例，使用顺序消费模式
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 创建独立的Consumer实例，使用独立的Consumer Group
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(rocketMqConfig.getServerAddr());
        
        // 使用配置中心的消费者配置
        RocketMqConfig.ConsumerConfig consumerConfig = rocketMqConfig.getConsumer();
        if (consumerConfig != null) {
            consumer.setConsumeThreadMin(consumerConfig.getConsumeThreadMin());
            consumer.setConsumeThreadMax(consumerConfig.getConsumeThreadMax());
            consumer.setMaxReconsumeTimes(consumerConfig.getMaxReconsumeTimes());
            consumer.setConsumeMessageBatchMaxSize(consumerConfig.getConsumeMessageBatchMaxSize());
            consumer.setConsumeTimeout(consumerConfig.getConsumeTimeout());
        }
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
//        consumer.setConsumeTimestamp("20251111000000"); // 格式：yyyyMMddHHmmss


        try {
            // 订阅C2C消息Topic
            consumer.subscribe(C2C_TOPIC, "*");
            log.info("{}订阅Topic成功: {}", TAG, C2C_TOPIC);
            
            // 注册顺序消息监听器（保证消息顺序性）
            consumer.registerMessageListener(new RocketMQOrderConsumerListener(this::handleEvent));
            
            // 启动Consumer
            consumer.start();
            log.info("{}初始化完成，开始监听主题: {}, Consumer Group: {}, 消费模式: 顺序消费", 
                TAG, C2C_TOPIC, CONSUMER_GROUP);
            
        } catch (MQClientException e) {
            log.error("{}初始化失败", TAG, e);
            throw e;
        }
    }

    /**
     * 处理C2C消息事件（顺序消费）
     */
    public void handleEvent(String topicName, com.xzll.common.rocketmq.ClusterEvent clusterEvent) {
        Integer clusterEventType = clusterEvent.getClusterEventType();
        if (Objects.isNull(clusterEventType)) {
            log.error("缺少必填参数clusterEventType，不处理，请检查消息发送方");
            return;
        }
        switch (clusterEventType) {
            case ImConstant.ClusterEventTypeConstant.C2C_SEND_MSG:
                C2CSendMsgAO c2CMsgRequestDto = JSONUtil.toBean(clusterEvent.getData(), C2CSendMsgAO.class);
                c2CSendMsgHandler.sendC2CMsgDeal(c2CMsgRequestDto);
                return;
            case ImConstant.ClusterEventTypeConstant.C2C_OFF_LINE_MSG:
                C2COffLineMsgAO c2COffLineMsgAo = JSONUtil.toBean(clusterEvent.getData(), C2COffLineMsgAO.class);
                //接传递C2COffLineMsgAO，离线消息处理应该更新状态而不是保存新消息
                c2COffLineMsgHandler.sendC2CMsgDeal(c2COffLineMsgAo);
                return;
            case ImConstant.ClusterEventTypeConstant.C2C_CLIENT_RECEIVED_ACK_MSG:
                C2CReceivedMsgAckAO c2CReceivedMsgAckAo = JSONUtil.toBean(clusterEvent.getData(), C2CReceivedMsgAckAO.class);
                c2CClientReceivedAckMsgHandler.clientReceivedAckMsgDeal(c2CReceivedMsgAckAo);
                return;
            case ImConstant.ClusterEventTypeConstant.C2C_CLIENT_WITHDRAW_MSG:
                C2CWithdrawMsgAO c2CWithdrawMsgAo = JSONUtil.toBean(clusterEvent.getData(), C2CWithdrawMsgAO.class);
                c2CClientWithdrawMsgHandler.clientWithdrawMsgDeal(c2CWithdrawMsgAo);
                return;
            default:
                log.warn("不适配的事件类型:{},请检查", clusterEvent);
        }
    }
}
