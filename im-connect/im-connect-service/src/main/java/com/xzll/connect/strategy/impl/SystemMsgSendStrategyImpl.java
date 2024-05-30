//package com.xzll.connect.strategy.impl;
//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//
//import com.xzll.connect.pojo.MsgBaseResponse;
//import com.xzll.connect.pojo.ao.MsgBaseRequest;
//import com.xzll.connect.pojo.base.BaseResponse;
//import com.xzll.connect.pojo.constant.UserRedisConstant;
//import com.xzll.connect.pojo.dto.MessageInfoDTO;
//import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
//import com.xzll.connect.pojo.dto.ServerInfoDTO;
//import com.xzll.connect.pojo.dto.SystemMsgRequestDTO;
//import com.xzll.connect.pojo.enums.MsgStatusEnum;
//import com.xzll.connect.pojo.enums.MsgTypeEnum;
//import com.xzll.connect.pojo.response.dto.ServerReceivedMsgAckDTO;
//import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
//import com.xzll.connect.strategy.MsgHandlerStrategy;
//import io.netty.channel.Channel;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.cache.RedisCache;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//import org.springframework.stereotype.Service;
//
//import javax.validation.constraints.NotNull;
//import java.text.MessageFormat;
//import java.util.Objects;
//
//
///**
// * @Author: hzz
// * @Date: 2022/2/18 15:20:10
// * @Description: 系统消息
// */
//@Slf4j
//@Service
//public class SystemMsgSendStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {
//
//    private static final String TAG = "[企业总线发送系统消息]_";
//    private static final String RECEIVE_URL = "http://{0}:{1}/msg/transferSendMsg";
//
//    @Autowired
//    private ThreadPoolTaskExecutor threadPool;
//    @Autowired
//    private RedisCache redisCache;
//    @Autowired
//    private ObjectMapper objectMapper;
//    @Autowired
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
//        return MsgBaseRequest.checkSupport(msgType, MsgTypeEnum.FirstLevelMsgType.SYSTEM_MSG.getCode(), MsgTypeEnum.SecondLevelMsgType.SYSTEM.getCode());
//    }
//
//    /**
//     * 根据不同类型适配不同的消息格式
//     *
//     * @param msgBaseRequest
//     * @return
//     */
//    private SystemMsgRequestDTO supportPojo(MsgBaseRequest msgBaseRequest) {
//        SystemMsgRequestDTO packet = objectMapper.convertValue(msgBaseRequest.getBody(), SystemMsgRequestDTO.class);
//        packet.setMsgCreateTime(System.currentTimeMillis());
//        packet.setMsgType(msgBaseRequest.getMsgType());
//        return packet;
//    }
//
//
//    @Override
//    public BaseResponse<MsgBaseResponse> exchange(MsgBaseRequest msgBaseRequest) {
//        log.info((TAG + "exchange_method_start."));
//
//        SystemMsgRequestDTO packet = this.supportPojo(msgBaseRequest);
//        //系统调用，异步执行
//        threadPool.execute(() -> {
//            //2. 获取接收人登录，服务信息，并根据状态进行处理
//            ReceiveUserDataDTO receiveUserData = getReceiveUserDataTemplate(packet.getToId(), this.redisCache);
//
//            String channelIdByUserId = receiveUserData.getChannelIdByUserId();
//            Channel targetChannel = receiveUserData.getTargetChannel();
//            String serverJson = receiveUserData.getServerJson();
//            String userStatus = receiveUserData.getUserStatus();
//            ServerInfoDTO serverInfoDTO = receiveUserData.getServerInfoDTO();
//
//            log.info((TAG + "接收者id:{},在线状态:{},channelId:{},serverInfo:{}"), packet.getToId(), userStatus, channelIdByUserId, serverInfoDTO);
//            //2.2 根据接收人状态做对应的处理
//            if (null != targetChannel && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus)) {
//                log.info((TAG + "用户{}在线且在本台机器上,将直接发送"), packet.getToId());
//                super.msgSendTemplate(TAG, targetChannel, JsonUtil.toJson(msgBaseRequest));
//            } else if (null == userStatus && null == targetChannel) {
//                log.info((TAG + "用户{}不在线，将消息保存至离线表中"), packet.getToId());
//                this.systemMsgOfflineStatusUpdate(packet);
//            } else if (null == targetChannel && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus) && !StringUtil.isNullOrBlank(serverJson)) {
//                log.info((TAG + "用户{}在线但是不在该机器上,跳转到用户所在的服务器,服务器信息:{}"), packet.getToId(), serverJson);
//                String requestUrl = MessageFormat.format(RECEIVE_URL, serverInfoDTO.getAddr(), String.valueOf(serverInfoDTO.getPort()));
//                super.msgTransferTemplate(requestUrl, TAG, JsonUtil.toJson(msgBaseRequest));
//            }
//        });
//        log.info((TAG + "exchange_method_end."));
//        //last. 返回接收到消息ack给system sender
//        ServerReceivedMsgAckDTO serverReceivedMsgAckDTO = getServerReceivedMsgAckVO(packet, true);
//        MsgBaseResponse msgBaseResponse = MsgBaseResponse.buildPushToClientData(msgBaseRequest.getMsgType(), serverReceivedMsgAckDTO);
//        return responseServerReceiveAckToSystem(msgBaseResponse, true);
//    }
//
//    @Override
//    public BaseResponse receiveAndSendMsg(MsgBaseRequest msgBaseRequest) {
//
//        log.info((TAG + "receiveAndSendMsg_method_start."));
//        SystemMsgRequestDTO packet = supportPojo(msgBaseRequest);
//
//        ReceiveUserDataDTO receiveUserData = getReceiveUserDataTemplate(packet.getToId(), this.redisCache);
//
//        String channelIdByUserId = receiveUserData.getChannelIdByUserId();
//        Channel targetChannel = receiveUserData.getTargetChannel();
//        String userStatus = receiveUserData.getUserStatus();
//        ServerInfoDTO serverInfoDTO = receiveUserData.getServerInfoDTO();
//
//        log.info((TAG + "接收者id:{},在线状态:{},channelId:{},serverInfo:{}"), packet.getToId(), userStatus, channelIdByUserId, serverInfoDTO);
//
//        //二次校验接收人在线状态
//        if (!StringUtil.isNullOrBlank(userStatus) && null != targetChannel) {
//            log.info((TAG + "跳转_发送跳转过来的消息,用户{}在线且在本台机器上,将直接发送"), packet.getToId());
//            super.msgSendTemplate(TAG, targetChannel, JsonUtil.toJson(msgBaseRequest));
//        } else {
//            log.info((TAG + "跳转后用户{}不在线,将消息保存至离线表中"), packet.getToId());
//            this.systemMsgOfflineStatusUpdate(packet);
//        }
//        log.info((TAG + "receiveAndSendMsg_method_end."));
//        return BaseResponse.returnResultSuccess("跳转消息成功");
//    }
//
//
//    /**
//     * 更新系统消息为离线
//     *
//     * @param packet
//     */
//    private void systemMsgOfflineStatusUpdate(SystemMsgRequestDTO packet) {
//        MessageInfoDTO build = MessageInfoDTO.builder()
//                .sessionId(packet.getSessionId())
//                .msgId(packet.getMsgId())
//                .fromUserId(packet.getFromId())
//                .toUserId(packet.getToId())
//                .isOffline(MsgStatusEnum.MsgOfflineStatus.YES.getCode())
//                .updateTime(System.currentTimeMillis())
//                .build();
//        // 更新系统消息为离线
//        int updatedCount = msgService.updateMultiMessageData(build);
//        log.info("更新系统消息为离线，msgId:{},sessionId:{},结果:{}", packet.getMsgId(), packet.getSessionId(), updatedCount);
//    }
//
//}
