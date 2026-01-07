package com.xzll.connect.cluster.provider;

import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.FriendRequestPushAO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.connect.cluster.mq.RocketMqProducerWrap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友申请推送MQ生产者
 */
@Slf4j
@Component
public class FriendRequestPushProvider {

    private static final String FRIEND_TOPIC = ImConstant.TopicConstant.XZLL_C2CMSG_TOPIC;

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    /**
     * 发送好友申请推送消息
     *
     * @param dto
     * @return
     */
    public boolean sendFriendRequestPush(FriendRequestPushAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.FRIEND_REQUEST_PUSH);
            result = rocketMqProducerWrap.sendClusterEvent(FRIEND_TOPIC, clusterEvent, dto.getRequestId());
            log.info("往MQ发送好友申请推送消息结果:{}, 申请ID:{}", result, dto.getRequestId());
        } catch (Exception e) {
            log.error("往MQ发送好友申请推送消息失败:", e);
        }
        return result;
    }

    /**
     * 发送好友申请处理结果推送消息
     *
     * @param dto
     * @return
     */
    public boolean sendFriendRequestHandlePush(FriendRequestPushAO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.FRIEND_REQUEST_HANDLE_PUSH);
            result = rocketMqProducerWrap.sendClusterEvent(FRIEND_TOPIC, clusterEvent, dto.getRequestId());
            log.info("往MQ发送好友申请处理结果推送消息结果:{}, 申请ID:{}", result, dto.getRequestId());
        } catch (Exception e) {
            log.error("往MQ发送好友申请处理结果推送消息失败:", e);
        }
        return result;
    }

}
