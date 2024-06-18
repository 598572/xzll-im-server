//package com.xzll.connect.strategy.impl;
//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//
//import com.xzll.connect.netty.channel.ChannelManager;
//import com.xzll.connect.pojo.MsgBaseResponse;
//import com.xzll.connect.pojo.ao.MsgBaseRequest;
//import com.xzll.connect.pojo.base.BaseResponse;
//import com.xzll.common.constant.UserRedisConstant;
//import com.xzll.connect.pojo.dto.MessageInfoDTO;
//import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
//import com.xzll.connect.pojo.dto.ServerInfoDTO;
//import com.xzll.connect.pojo.dto.WithdrawMsgRequestDTO;
//import com.xzll.common.constant.MsgStatusEnum;
//import com.xzll.common.constant.MsgTypeEnum;
//import com.xzll.connect.pojo.response.dto.ServerReceivedMsgAckDTO;
//import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
//import com.xzll.connect.strategy.MsgHandlerStrategy;
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelHandlerContext;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.cache.RedisCache;
//import org.springframework.stereotype.Service;
//
//import javax.validation.constraints.NotNull;
//import java.text.MessageFormat;
//import java.util.Collections;
//import java.util.Objects;
//
//
///**
// * @Author: hzz
// * @Date: 2022/1/14 15:20:10
// * @Description: 撤回消息发送策略
// */
//@Slf4j
//@Service
//public class WithdrawMsgSendStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {
//
//    private static final String RECEIVE_URL = "http://{0}:{1}/msg/transferWithdrawMsg";
//    private static final String TAG = "[客户端发送撤回消息]_";
//
//    @Resource
//    private RedisCache redisCache;
//    @Resource
//    private ObjectMapper objectMapper;
//    @Resource
//    private MsgService msgService;
//
//    /**
//     * 策略适配
//     *
//     * @param msgType
//     * @return
//     */
//    @Override
//    public boolean support(@NotNull MsgBaseRequest.MsgType msgType) {
//        return MsgBaseRequest.checkSupport(msgType, MsgTypeEnum.FirstLevelMsgType.COMMAND_MSG.getCode(), MsgTypeEnum.SecondLevelMsgType.WITHDRAW.getCode());
//    }
//
//    /**
//     * 根据不同类型适配不同的消息格式
//     *
//     * @param msgBaseRequest
//     * @return
//     */
//    public WithdrawMsgRequestDTO supportPojo(MsgBaseRequest msgBaseRequest) {
//        WithdrawMsgRequestDTO packet = objectMapper.convertValue(msgBaseRequest.getBody(), WithdrawMsgRequestDTO.class);
//        packet.setMsgType(msgBaseRequest.getMsgType());
//        return packet;
//    }
//
//    @Override
//    public void exchange(ChannelHandlerContext ctx, MsgBaseRequest msgBaseRequest) {
//        log.info((TAG + "exchange_method_start."));
//
//        //1. 处理会话记录和消息记录以及server ack
//        WithdrawMsgRequestDTO packet = this.supportPojo(msgBaseRequest);
//
//        // 构建&响应服务端是否接收成功消息
//        ServerReceivedMsgAckDTO serverReceivedMsgAckDTO = getServerReceivedMsgAckVO(packet, true);
//        MsgBaseResponse<ServerReceivedMsgAckDTO> msgBaseResponse = MsgBaseResponse.buildPushToClientData(packet.getMsgType(), serverReceivedMsgAckDTO);
//        super.responseServerReceiveAckToSender(ctx, msgBaseResponse);
//
//        //2. 获取接收人登录，服务信息，根据状态进行处理
//        ReceiveUserDataDTO receiveUserData = super.getReceiveUserDataTemplate(packet.getToUserId(), this.redisCache);
//
//        String channelIdByUserId = receiveUserData.getChannelIdByUserId();
//        Channel targetChannel = receiveUserData.getTargetChannel();
//        String serverJson = receiveUserData.getServerJson();
//        String userStatus = receiveUserData.getUserStatus();
//        ServerInfoDTO serverInfoDTO = receiveUserData.getServerInfoDTO();
//        log.info((TAG + "接收者id:{},在线状态:{},channelId:{},serverInfo:{}"), packet.getToUserId(), userStatus, channelIdByUserId, serverInfoDTO);
//
//        //3. 根据接收人状态做对应的处理
//        if (null != targetChannel && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus)) {
//            // 直接发送
//            log.info((TAG + "用户{}在线且在本台机器上,将直接发送"), packet.getToUserId());
//            super.msgSendTemplate(TAG, targetChannel, JsonUtil.toJson(msgBaseRequest));
//        } else if (null == userStatus && null == targetChannel) {
//            log.info((TAG + "用户{}不在线，将db中消息数据更新为撤回消息"), packet.getToUserId());
//            // 更新消息状态为撤回
//            this.withdrawMsgStatusUpdate(packet);
//        } else if (Objects.isNull(targetChannel) && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus)
//                && !StringUtil.isNullOrBlank(serverJson)) {
//            log.info((TAG + "用户{}在线但是不在该机器上,跳转到用户所在的服务器,服务器信息:{}"), packet.getToUserId(), serverJson);
//            String requestUrl = MessageFormat.format(RECEIVE_URL, serverInfoDTO.getAddr(), String.valueOf(serverInfoDTO.getPort()));
//            super.msgTransferTemplate(requestUrl, TAG, JsonUtil.toJson(msgBaseRequest));
//        }
//        //测试消息转发
////        String requestUrl = MessageFormat.format(RECEIVE_URL, serverInfoDTO.getAddr(), String.valueOf(serverInfoDTO.getPort()));
////        super.msgTransferTemplate(requestUrl, TAG, JsonUtil.toJson(msgBaseRequest));
//
//        log.info((TAG + "exchange_method_end."));
//
//    }
//
//
//    @Override
//    public BaseResponse receiveAndSendMsg(MsgBaseRequest msg) {
//        log.info((TAG + "receiveAndSendMsg_method_start."));
//
//        WithdrawMsgRequestDTO packet = supportPojo(msg);
//
//        String channelIdByUserId = ChannelManager.getChannelIdByUserId(String.valueOf(packet.getToUserId()));
//        Channel targetChannel = ChannelManager.findChannel(channelIdByUserId);
//
//        String userStatus = redisCache.get(UserRedisConstant.LOGIN_STATUS_PREFIX + channelIdByUserId);
//        //二次校验接收人在线状态
//        if (!StringUtil.isNullOrBlank(userStatus) && null != targetChannel) {
//            log.info((TAG + "跳转后用户{}在线,将直接发送消息"), packet.getToUserId());
//            super.msgSendTemplate(TAG, targetChannel, JsonUtil.toJson(msg));
//        } else {
//            log.info((TAG + "跳转后用户{}不在线,将db中消息数据更新为撤回消息"), packet.getToUserId());
//            this.withdrawMsgStatusUpdate(packet);
//        }
//
//        log.info((TAG + "receiveAndSendMsg_method_end."));
//        return BaseResponse.returnResultSuccess("跳转消息成功");
//    }
//
//    /**
//     * 更新消息状态为撤回
//     *
//     * @param packet
//     */
//    public void withdrawMsgStatusUpdate(WithdrawMsgRequestDTO packet) {
//        MessageInfoDTO build = MessageInfoDTO.builder()
//                .sessionId(packet.getSessionId())
//                .msgId(packet.getMsgId())
//                .msgIds(Collections.singletonList(packet.getMsgId()))
//                .fromUserId(packet.getFromUserId())
//                .toUserId(packet.getToUserId())
//                .isWithdraw(MsgStatusEnum.MsgWithdrawStatus.YES.getCode())
//                .updateTime(System.currentTimeMillis())
//                .build();
//        // 更新消息为离线 TODO 暂时不更新会话更新时间，也有可能需要更新会话时间
//        int updatedCount = msgService.updateMultiMessageData(build);
//        if (updatedCount > 0) {
//            log.info("更新撤回消息_成功,msgId:{},sessionId:{},数量:{}", packet.getMsgId(), packet.getSessionId(), updatedCount);
//            //
//        } else {
//            log.info("更新撤回消息_没有符合条件的消息，msgId:{},sessionId:{}", packet.getMsgId(), packet.getSessionId());
//        }
//    }
//
//}
