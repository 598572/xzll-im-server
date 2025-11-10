package com.xzll.business.cluster.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.mq.RocketMqConsumerWrap;
import com.xzll.business.handler.c2c.C2CClientWithdrawMsgHandler;
import com.xzll.business.handler.c2c.C2CSendMsgHandler;
import com.xzll.business.handler.c2c.C2CClientReceivedAckMsgHandler;
import com.xzll.business.handler.c2c.C2COffLineMsgHandler;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
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
    @Resource
    private C2CClientWithdrawMsgHandler c2CClientWithdrawMsgHandler;

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
                // 转换为C2CSendMsgAO进行处理
                C2CSendMsgAO c2CSendMsgAO = convertToC2CSendMsgAO(c2COffLineMsgAo);
                c2COffLineMsgHandler.sendC2CMsgDeal(c2CSendMsgAO);
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
    
    /**
     * 将C2COffLineMsgAO转换为C2CSendMsgAO
     */
    private C2CSendMsgAO convertToC2CSendMsgAO(C2COffLineMsgAO offLineMsgAO) {
        log.debug("【C2CMsgEventConsumer-转换开始】离线消息转换 - clientMsgId: {}, msgId: {}, from: {}, to: {}",
            offLineMsgAO.getClientMsgId(), offLineMsgAO.getMsgId(), offLineMsgAO.getFromUserId(), offLineMsgAO.getToUserId());
        
        C2CSendMsgAO sendMsgAO = new C2CSendMsgAO();
        sendMsgAO.setClientMsgId(offLineMsgAO.getClientMsgId()); // 修复：设置客户端消息ID
        sendMsgAO.setFromUserId(offLineMsgAO.getFromUserId());
        sendMsgAO.setToUserId(offLineMsgAO.getToUserId());
        sendMsgAO.setMsgContent(offLineMsgAO.getMsgContent());
        sendMsgAO.setMsgFormat(offLineMsgAO.getMsgFormat());
        sendMsgAO.setMsgId(offLineMsgAO.getMsgId());
        sendMsgAO.setChatId(offLineMsgAO.getChatId());
        sendMsgAO.setUrl(offLineMsgAO.getUrl());
        sendMsgAO.setMsgCreateTime(offLineMsgAO.getMsgCreateTime());
        
        log.debug("【C2CMsgEventConsumer-转换完成】转换结果 - clientMsgId: {}, msgId: {}, from: {}, to: {}",
            sendMsgAO.getClientMsgId(), sendMsgAO.getMsgId(), sendMsgAO.getFromUserId(), sendMsgAO.getToUserId());
        
        return sendMsgAO;
    }
}
