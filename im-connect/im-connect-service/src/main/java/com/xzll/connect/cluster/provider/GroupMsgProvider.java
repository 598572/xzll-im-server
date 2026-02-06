package com.xzll.connect.cluster.provider;


import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.GroupOffLineMsgAO;
import com.xzll.common.pojo.request.GroupSendMsgAO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.connect.cluster.mq.RocketMqProducerWrap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群聊消息 MQ 提供者（发送MQ消息）
 */
@Slf4j
@Component
public class GroupMsgProvider {

    private static final String GROUP_TOPIC = ImConstant.TopicConstant.XZLL_GROUPMSG_TOPIC;
    private static final String TAG = "[群聊消息MQ提供者]_";

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    /**
     * 发送群聊消息到 MQ（广播模式）
     *
     * @param dto 群消息 AO
     * @return 发送结果
     */
    public boolean sendGroupMsg(GroupSendMsgAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.GROUP_SEND_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(GROUP_TOPIC, clusterEvent, dto.getMsgId());
            log.info("{}往MQ发送群聊消息结果 - groupId:{}, msgId:{}, result:{}",
                TAG, dto.getGroupId(), dto.getMsgId(), result);
        } catch (Exception e) {
            log.error("{}往MQ发送群聊消息失败 - groupId:{}, msgId:{}",
                TAG, dto.getGroupId(), dto.getMsgId(), e);
        }
        return result;
    }

    /**
     * 往MQ发送群离线消息（用于更新消息状态）
     *
     * @param dto 离线消息 AO
     * @return 发送结果
     */
    public boolean offLineMsg(GroupOffLineMsgAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.GROUP_OFF_LINE_MSG);
            result = rocketMqProducerWrap.sendClusterEvent(GROUP_TOPIC, clusterEvent, dto.getMsgId());
            log.info("{}往MQ发送群离线消息结果 - groupId:{}, msgId:{}, result:{}",
                TAG, dto.getGroupId(), dto.getMsgId(), result);
        } catch (Exception e) {
            log.error("{}往MQ发送群离线消息失败 - groupId:{}, msgId:{}",
                TAG, dto.getGroupId(), dto.getMsgId(), e);
        }
        return result;
    }
}
