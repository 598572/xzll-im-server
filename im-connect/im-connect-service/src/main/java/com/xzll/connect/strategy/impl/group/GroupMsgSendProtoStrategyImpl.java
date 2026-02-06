package com.xzll.connect.strategy.impl.group;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ProtoResponseCode;
import com.xzll.common.pojo.request.GroupSendMsgAO;
import com.xzll.common.util.ChatIdUtils;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.connect.cluster.provider.GroupMsgProvider;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.ProtoMsgHandlerStrategy;
import com.xzll.grpc.GroupSendReq;
import com.xzll.grpc.ImProtoRequest;
import com.xzll.grpc.ImProtoResponse;
import com.xzll.grpc.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import com.xzll.common.util.msgId.SnowflakeIdService;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群聊消息发送策略（MQ广播方案）
 *
 * 核心职责：
 * 1. 接收客户端发送的群聊消息
 * 2. 校验用户是否在群中
 * 3. 发送MQ广播消息（由各服务器消费后推送）
 * 4. 返回成功响应
 *
 * MQ广播方案特点：
 * - 不遍历成员列表
 * - 不跨服务器gRPC调用
 * - 只负责发送MQ，由各服务器消费MQ后推送
 */
@Slf4j
@Service
public class GroupMsgSendProtoStrategyImpl extends MsgHandlerCommonAbstract implements ProtoMsgHandlerStrategy {

    private static final String TAG = "[群聊消息发送策略]_";

    @Resource
    private RedissonUtils redissonUtils;
    @Resource
    private GroupMsgProvider groupMsgProvider;
    @Resource
    private SnowflakeIdService snowflakeIdService;

    @Override
    public MsgType supportMsgType() {
        return MsgType.GROUP_SEND;
    }

    /**
     * 处理客户端发送的群聊消息（MQ广播方案）
     *
     * 流程说明：
     * 1. 解析 GroupSendReq
     * 2. 校验用户是否在群中
     * 3. 发送MQ广播消息
     * 4. 返回成功响应
     */
    @Override
    public void exchange(ChannelHandlerContext ctx, ImProtoRequest protoRequest) {
        log.debug("{}客户端发送群聊消息_开始", TAG);

        try {
            // 打印 ImProtoRequest 详细信息
            log.info("{}收到客户端消息 - 消息类型: {}, Payload大小: {} bytes",
                TAG, protoRequest.getType(), protoRequest.getPayload().size());

            // 解析 GroupSendReq
            GroupSendReq req = GroupSendReq.parseFrom(protoRequest.getPayload());

            // 打印消息详细内容
            log.debug("{}【步骤1-接收消息】msgId: {}, groupId: {}, from: {}, format: {}, time: {}, contentLength: {}",
                TAG, req.getMsgId(), req.getGroupId(),
                req.getFrom(), req.getFormat(), req.getTime(), req.getContent().length());

            GroupSendMsgAO packet = convertToAO(req);

            // 打印转换后的AO对象信息
            log.debug("{}【步骤2-转换完成】转换后AO - msgId: {}, groupId: {}, fromUserId: {}",
                TAG, packet.getMsgId(), packet.getGroupId(), packet.getFromUserId());

            // 【步骤3】校验用户是否在群中
            // 开发阶段暂时跳过校验，直接通过
            // TODO: 生产环境必须启用校验，取消下面注释
            /*
            if (!isGroupMember(packet.getGroupId(), packet.getFromUserId())) {
                log.warn("{}【步骤3-校验失败】用户不在群中 - groupId:{}, userId:{}",
                    TAG, packet.getGroupId(), packet.getFromUserId());
                sendErrorResponse(ctx, "您不在该群中");
                return;
            }
            */
            log.debug("{}【步骤3-跳过校验】开发阶段暂未校验群成员 - groupId:{}, userId:{}",
                TAG, packet.getGroupId(), packet.getFromUserId());

            // 【步骤4】发送MQ广播消息
            boolean sendResult = groupMsgProvider.sendGroupMsg(packet);
            if (!sendResult) {
                log.error("{}【步骤4-MQ发送失败】往MQ发送群聊消息失败 - groupId:{}, msgId:{}",
                    TAG, packet.getGroupId(), packet.getMsgId());
                sendErrorResponse(ctx, "发送失败，请稍后重试");
                return;
            }

            log.info("{}【步骤4-MQ发送成功】往MQ发送群聊消息成功 - groupId:{}, msgId:{}",
                TAG, packet.getGroupId(), packet.getMsgId());

            // 【步骤5】返回成功响应给客户端
            sendSuccessResponse(ctx, packet);

            log.debug("{}客户端发送群聊消息_结束", TAG);

        } catch (InvalidProtocolBufferException e) {
            log.error("{}解析 protobuf 消息失败", TAG, e);
        } catch (Exception e) {
            log.error("{}处理群聊消息异常", TAG, e);
        }
    }

    /**
     * 将 GroupSendReq 转换为 GroupSendMsgAO
     */
    private GroupSendMsgAO convertToAO(GroupSendReq req) {
        GroupSendMsgAO ao = new GroupSendMsgAO();

        // GroupSendReq没有clientMsgId字段，所以不设置
        // ao.setClientMsgId(...);

        // fixed64 -> string（如果客户端传了msgId则使用，否则服务端生成）
        String msgId;
        if (req.getMsgId() > 0) {
            msgId = ProtoConverterUtil.longToSnowflakeString(req.getMsgId());
            log.debug("{}使用客户端传递的msgId: {}", TAG, msgId);
        } else {
            msgId = snowflakeIdService.generateSimpleMessageId();
            log.info("{}客户端msgId为空，服务端生成新msgId: {}", TAG, msgId);
        }
        ao.setMsgId(msgId);

        // fixed64 -> string
        ao.setFromUserId(ProtoConverterUtil.longToSnowflakeString(req.getFrom()));
        ao.setGroupId(ProtoConverterUtil.longToSnowflakeString(req.getGroupId()));
        ao.setMsgFormat(req.getFormat());
        ao.setMsgContent(req.getContent());
        ao.setMsgCreateTime(req.getTime() > 0 ? req.getTime() : System.currentTimeMillis());

        // chatId 根据groupId生成（群聊会话ID）
        ao.setChatId(ChatIdUtils.buildGroupChatId(ImConstant.DEFAULT_BIZ_TYPE, req.getGroupId()));

        return ao;
    }

    /**
     * 校验用户是否在群中
     * 注意：此方法暂时未实现，需要后续补充
     *
     * @param groupId 群ID
     * @param userId  用户ID
     * @return true-在群中，false-不在群中
     */
    private boolean isGroupMember(String groupId, String userId) {
        // TODO: 实现群成员校验逻辑
        // 1. 查询Redis缓存
        // 2. 缓存未命中时查询数据库
        return true; // 暂时返回true
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(ChannelHandlerContext ctx, GroupSendMsgAO packet) {
        try {
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(MsgType.GROUP_SEND)
                .setCode(ProtoResponseCode.SUCCESS)
                .build();

            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);

            ctx.writeAndFlush(new BinaryWebSocketFrame(buf))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.debug("{}【发送响应成功】msgId: {}",
                            TAG, packet.getMsgId());
                    } else {
                        log.warn("{}【发送响应失败】msgId: {}",
                            TAG, packet.getMsgId());
                    }
                });

        } catch (Exception e) {
            log.error("{}发送成功响应异常 - msgId:{}", TAG, packet.getMsgId(), e);
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String errorMsg) {
        try {
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(MsgType.GROUP_SEND)
                .setCode(ProtoResponseCode.PARAM_ERROR)
                .build();

            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);

            ctx.writeAndFlush(new BinaryWebSocketFrame(buf));

        } catch (Exception e) {
            log.error("{}发送错误响应异常 - error:{}", TAG, errorMsg, e);
        }
    }
}
