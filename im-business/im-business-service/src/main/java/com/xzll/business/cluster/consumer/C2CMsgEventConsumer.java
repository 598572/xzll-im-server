package com.xzll.business.cluster.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.mq.RocketMqConsumerWrap;
import com.xzll.business.handler.c2c.C2CSendMsgHandler;
import com.xzll.business.handler.c2c.C2CClientReceivedAckMsgHandler;
import com.xzll.business.handler.c2c.C2COffLineMsgHandler;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
import com.xzll.common.pojo.OffLineMsgDTO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2023/3/1 11:25:10
 * @Description: 单聊消息消费方，此消费者为顺序消费（同一个消息id会进入同一个队列，不同消息id之间不影响） 避免乱序导致异常（如：某消息id=1的消息 在更新时还没插入）
 */
@Slf4j
@Component
public class C2CMsgEventConsumer implements RocketMQClusterEventListener, InitializingBean {


    private static final String C2C_TOPIC = ImConstant.TopicConstant.XZLL_C2CMSG_TOPIC;

    /**
     * 顺序消费（根据msgId）
     */
    @Resource
    private RocketMqConsumerWrap consumer;
    @Resource
    private C2CSendMsgHandler c2CSendMsgHandler;
    @Resource
    private C2COffLineMsgHandler c2COffLineMsgHandler;
    @Resource
    private C2CClientReceivedAckMsgHandler c2CClientReceivedAckMsgHandler;

    /**
     * 初始化该类要监听的topic 并且调用RocketMqCustomConsumer的subscribe方法，进行订阅和启动consumer
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() {
        List<String> topics = new ArrayList<>();
        topics.add(C2C_TOPIC);
        consumer.subscribeByOrderConsumer(topics, this);
    }

    @Override
    public void handleEvent(String topicName, ClusterEvent clusterEvent) {
        Integer clusterEventType = clusterEvent.getClusterEventType();
        if (Objects.isNull(clusterEventType)) {
            log.error("缺少必填参数clusterEventType，不处理，请检查消息发送方");
            return;
        }
        switch (clusterEventType) {
            case ImConstant.ClusterEventTypeConstant.C2C_SEND_MSG:
                C2CMsgRequestDTO c2CMsgRequestDTO = JSONUtil.toBean(clusterEvent.getData(), C2CMsgRequestDTO.class);
                c2CSendMsgHandler.sendC2CMsgDeal(c2CMsgRequestDTO);
                return;
            case ImConstant.ClusterEventTypeConstant.C2C_OFF_LINE_MSG:
                OffLineMsgDTO offLineMsgDTO = JSONUtil.toBean(clusterEvent.getData(), OffLineMsgDTO.class);
                c2COffLineMsgHandler.offLineMsgDeal(offLineMsgDTO);
                return;
            case ImConstant.ClusterEventTypeConstant.C2C_CLIENT_RECEIVED_ACK_MSG:
                ClientReceivedMsgAckDTO clientReceivedMsgAckDTO = JSONUtil.toBean(clusterEvent.getData(), ClientReceivedMsgAckDTO.class);
                c2CClientReceivedAckMsgHandler.clientReceivedAckMsgDeal(clientReceivedMsgAckDTO);
                return;
            default:
                log.warn("不适配的事件类型:{},请检查", clusterEvent);
        }
    }
}
