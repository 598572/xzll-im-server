package com.xzll.connect.cluster.provider;


import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
import com.xzll.common.pojo.OffLineMsgDTO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.connect.cluster.mq.RocketMqProducerWrap;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

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
    public boolean sendC2CMsg(C2CMsgRequestDTO dto) {
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
    public boolean offLineMsg(OffLineMsgDTO dto) {
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
    public boolean clientResponseAck(ClientReceivedMsgAckDTO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_CLIENT_RECEIVED_ACK_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(C2CMsgProvider.C2C_TOPIC, clusterEvent, dto.getMsgId());
            log.info("往mq发送离线消息结果:{}", result);
        } catch (Exception e) {
            log.error("往mq发送离线消息失败:", e);
        }
        return result;
    }
}
