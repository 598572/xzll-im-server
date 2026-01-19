package com.xzll.connect.cluster.provider;


import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.connect.cluster.mq.RocketMqProducerWrap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description: rocketMq 生产者包装类
 */
@Slf4j
@Component
public class C2CMsgProvider {

    private static final String C2C_TOPIC = ImConstant.TopicConstant.XZLL_C2CMSG_TOPIC;

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;


    /**
     * 发送消息，根据指定topic和事件数据
     *
     * @param dto
     * @return
     */
    public boolean sendC2CMsg(C2CSendMsgAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_SEND_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(C2CMsgProvider.C2C_TOPIC, clusterEvent, dto.getMsgId());
            log.info("往mq发送单聊消息结果:{}", result);
        } catch (Exception e) {
            log.error("往mq发送单聊消息失败:", e);
        }
        return result;
    }

    /**
     * 往mq 发送离线消息 （用于更新消息状态）
     *
     * @param dto
     * @return
     */
    public boolean offLineMsg(C2COffLineMsgAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_OFF_LINE_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(C2CMsgProvider.C2C_TOPIC, clusterEvent, dto.getMsgId());
            log.info("往mq发送离线消息结果:{}", result);
        } catch (Exception e) {
            log.error("往mq发送离线消息失败:", e);
        }
        return result;
    }

    /**
     * 往mq 发送接收方ack消息，用于更新消息状态以及响应给消息发送方ack
     *
     * @param dto
     * @return
     */
    public boolean clientResponseAck(C2CReceivedMsgAckAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_CLIENT_RECEIVED_ACK_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(C2CMsgProvider.C2C_TOPIC, clusterEvent, dto.getMsgId());
            log.info("往mq发送客户端ack消息结果:{}", result);
        } catch (Exception e) {
            log.error("往mq发送客户端ack消息失败:", e);
        }
        return result;
    }

    /**
     * 往mq 发送撤回消息，用于更新消息状态为撤回以及发撤回消息
     *
     * @param ao
     * @return
     */
    public boolean sendWithdrawMsg(C2CWithdrawMsgAO ao) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(ao));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_CLIENT_WITHDRAW_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(C2CMsgProvider.C2C_TOPIC, clusterEvent, ao.getMsgId());
            log.info("往mq发送客户端撤回消息结果:{}", result);
        } catch (Exception e) {
            log.error("往mq发送客户端撤回消息失败:", e);
        }
        return result;
    }
}
