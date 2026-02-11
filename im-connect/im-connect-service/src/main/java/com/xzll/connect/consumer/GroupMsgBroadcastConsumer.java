package com.xzll.connect.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.GroupSendMsgAO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.connect.config.RocketMqConfig;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.service.GroupServerMemberService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import com.xzll.grpc.ImProtoResponse;
import com.xzll.grpc.GroupMsgPush;
import com.xzll.grpc.MsgType;
import com.xzll.common.constant.ProtoResponseCode;

import java.util.List;
import java.util.Set;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群聊消息广播消费者（MQ广播方案核心组件 - Hash分片优化版）
 *
 * 核心职责：
 * 1. 消费RocketMQ消息（每台服务器都会收到相同的消息）
 * 2. 使用Redis Hash分片查询本地在线成员
 * 3. 推送给本地成员
 *
 * MA广播方案特点：
 * - 使用原生RocketMQ客户端
 * - 每台服务器独立处理本地成员
 * - 无需跨服务器gRPC调用
 * - 完全去中心化，无单点瓶颈
 *
 * 性能优化（Hash分片）：
 * - Redis Hash预计算分片信息
 * - 查询O(1)：HGET直接获取目标成员
 * - 零计算开销：无需retainAll交集运算
 * - 零本地内存：完全依赖Redis
 * - 适合大规模场景：单台50万在线用户
 */
@Slf4j
@Component
public class GroupMsgBroadcastConsumer implements InitializingBean {

    private static final String TAG = "[群聊消息广播消费者]_";
    private static final String GROUP_TOPIC = ImConstant.TopicConstant.XZLL_GROUPMSG_TOPIC;
    private static final String CONSUMER_GROUP = "GROUP_MSG_BROADCAST_CONSUMER";

    @Resource
    private LocalChannelManager localChannelManager;
    @Resource
    private RocketMqConfig rocketMqConfig;
    @Resource
    private GroupServerMemberService groupServerMemberService;

    private DefaultMQPushConsumer consumer;

    @Override
    public void afterPropertiesSet() throws MQClientException {
        // 初始化消费者
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);

        // 从Nacos配置中心读取NameServer地址
        consumer.setNamesrvAddr(rocketMqConfig.getServerAddr());
        log.info("{}从配置中心读取NameServer地址: {}", TAG, rocketMqConfig.getServerAddr());

        // 使用配置中心的消费者配置
        RocketMqConfig.ConsumerConfig consumerConfig = rocketMqConfig.getConsumer();
        if (consumerConfig != null) {
            consumer.setConsumeThreadMin(consumerConfig.getConsumeThreadMin());
            consumer.setConsumeThreadMax(consumerConfig.getConsumeThreadMax());
            consumer.setMaxReconsumeTimes(consumerConfig.getMaxReconsumeTimes());
            consumer.setConsumeMessageBatchMaxSize(consumerConfig.getConsumeMessageBatchMaxSize());
            consumer.setConsumeTimeout(consumerConfig.getConsumeTimeout());
            log.info("{}应用消费者配置: threadMin={}, threadMax={}, maxReconsumeTimes={}",
                TAG,
                consumerConfig.getConsumeThreadMin(),
                consumerConfig.getConsumeThreadMax(),
                consumerConfig.getMaxReconsumeTimes()
            );
        }

        // 设置消费起始位置
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);

        // 订阅Topic，订阅所有Tag
        consumer.subscribe(GROUP_TOPIC, "*");

        // 注册消息监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                List<MessageExt> msgs,
                ConsumeConcurrentlyContext context) {

                for (MessageExt msg : msgs) {
                    try {
                        // 解析消息
                        String messageJson = new String(msg.getBody(), "UTF-8");
                        log.info("{}接收到群聊消息 - msgId:{}, queueId:{}, offset:{}",
                            TAG, msg.getMsgId(), msg.getQueueId(), msg.getQueueOffset());

                        // 处理消息
                        processMessage(messageJson);

                    } catch (Exception e) {
                        log.error("{}处理消息失败 - msgId:{}", TAG, msg.getMsgId(), e);
                        // 返回稍后重试
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }

                // 消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        // 启动消费者
        consumer.start();
        log.info("{}群聊消息广播消费者启动成功 - topic:{}, group:{}, namesrv:{}",
            TAG, GROUP_TOPIC, CONSUMER_GROUP, rocketMqConfig.getServerAddr());
    }

    /**
     * 处理群聊消息
     */
    private void processMessage(String messageJson) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析ClusterEvent包装
            ClusterEvent clusterEvent = JSONUtil.toBean(messageJson, ClusterEvent.class);
            log.debug("{}【步骤1-解析ClusterEvent成功】eventType:{}, dataLength:{}",
                TAG, clusterEvent.getClusterEventType(),
                clusterEvent.getData() != null ? clusterEvent.getData().length() : 0);

            // 2. 解析真正的业务数据
            GroupSendMsgAO packet = JSONUtil.toBean(clusterEvent.getData(), GroupSendMsgAO.class);
            log.info("{}【步骤1-解析成功】msgId:{}, groupId:{}, fromUserId:{}",
                TAG, packet.getMsgId(), packet.getGroupId(), packet.getFromUserId());

            // 2. 查询本地在线成员（Hash分片方案）
            Set<String> localOnlineMembers = groupServerMemberService.getLocalGroupMembers(packet.getGroupId());

            if (localOnlineMembers == null || localOnlineMembers.isEmpty()) {
                log.debug("{}【步骤2-本地无成员】本地无在线成员 - groupId:{}", TAG, packet.getGroupId());
                return;
            }

            log.info("{}【步骤2-本地成员】本地在线成员数 - groupId:{}, count:{}, cost:{}ms",
                TAG, packet.getGroupId(), localOnlineMembers.size(), System.currentTimeMillis() - startTime);

            // 3. 推送给本地在线成员
            int successCount = 0;
            int failCount = 0;

            for (String userId : localOnlineMembers) {
                // 跳过发送者本人（发送者已经在发送方服务器收到响应）
                if (userId.equals(packet.getFromUserId())) {
                    log.debug("{}【步骤3-跳过发送者】跳过发送者本人 - userId:{}", TAG, userId);
                    continue;
                }

                Channel channel = localChannelManager.getChannelByUserId(userId);
                if (channel != null && channel.isActive()) {
                    boolean pushResult = pushToChannel(channel, packet);
                    if (pushResult) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } else {
                    log.warn("{}【步骤3-Channel无效】用户Channel不存在或不活跃 - userId:{}", TAG, userId);
                    failCount++;
                }
            }

            long totalCost = System.currentTimeMillis() - startTime;
            log.info("{}【步骤3-推送完成】groupId:{}, msgId:{}, localMembers:{}, success:{}, fail:{}, totalCost:{}ms",
                TAG, packet.getGroupId(), packet.getMsgId(),
                localOnlineMembers.size(), successCount, failCount, totalCost);

        } catch (Exception e) {
            log.error("{}处理群聊消息广播失败 - messageJson:{}", TAG, messageJson, e);
        }
    }


    /**
     * 推送消息给单个 Channel
     *
     * @param channel 目标Channel
     * @param packet  群消息AO
     * @return true-推送成功，false-推送失败
     */
    private boolean pushToChannel(Channel channel, GroupSendMsgAO packet) {
        try {
            io.netty.util.AttributeKey<String> userIdKey = io.netty.util.AttributeKey.valueOf(ImConstant.USER_ID);
            String userId = channel.attr(userIdKey).get();

            // 构建 GroupMsgPush
            GroupMsgPush pushMsg = GroupMsgPush.newBuilder()
                .setMsgId(ProtoConverterUtil.snowflakeStringToLong(packet.getMsgId()))
                .setFrom(ProtoConverterUtil.snowflakeStringToLong(packet.getFromUserId()))
                .setFromNickname(packet.getFromNickname() != null ? packet.getFromNickname() : "")
                .setFromAvatar(packet.getFromAvatar() != null ? packet.getFromAvatar() : "")
                .setGroupId(ProtoConverterUtil.snowflakeStringToLong(packet.getGroupId()))
                .setGroupName(packet.getGroupName() != null ? packet.getGroupName() : "")
                .setFormat(packet.getMsgFormat())
                .setContent(packet.getMsgContent())
                .setTime(packet.getMsgCreateTime())
                .setMemberCount(packet.getMemberCount() != null ? packet.getMemberCount() : 0)
                .build();

            // 构建 ImProtoResponse
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(MsgType.GROUP_MSG_PUSH)
                .setPayload(com.google.protobuf.ByteString.copyFrom(pushMsg.toByteArray()))
                .setCode(ProtoResponseCode.SUCCESS)
                .build();

            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);

            // 异步推送
            channel.writeAndFlush(new BinaryWebSocketFrame(buf))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.debug("{}【推送成功】userId:{}, msgId:{}, groupId:{}",
                            TAG, userId, packet.getMsgId(), packet.getGroupId());
                    } else {
                        log.warn("{}【推送失败】userId:{}, msgId:{}, groupId:{}",
                            TAG, userId, packet.getMsgId(), packet.getGroupId());
                    }
                });

            return true;

        } catch (Exception e) {
            log.error("{}【推送异常】msgId:{}", TAG, packet.getMsgId(), e);
            return false;
        }
    }
}
