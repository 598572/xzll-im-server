//package com.xzll.connect.strategy.impl;
//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import com.xzll.im.common.enums.MsgStatusEnum;
//import com.xzll.im.common.enums.MsgTypeEnum;
//import com.xzll.im.common.pojo.request.msg.MsgBaseRequest;
//import com.xzll.im.common.pojo.response.base.BaseResponse;
//import com.xzll.im.common.pojo.response.msg.MsgBaseResponse;
//import com.xzll.im.common.util.HttpComponet;
//import com.xzll.imcenter.domain.constant.UserRedisConstant;
//import com.xzll.imcenter.domain.request.dto.MessageInfoDTO;
//import com.xzll.imcenter.domain.request.dto.ReceiveUserDataDTO;
//import com.xzll.imcenter.domain.request.dto.ServerInfoDTO;
//import com.xzll.imcenter.domain.response.dto.ClientReceivedMsgAckDTO;
//import com.xzll.imcenter.service.MsgService;
//import com.xzll.imcenter.strategy.MsgHandlerCommonAbstract;
//import com.xzll.imcenter.strategy.MsgHandlerStrategy;
//import com.xzll.imcenter.util.ChannelManager;
//import com.xzll.imcenter.util.SpringBeanFactory;
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelFuture;
//import io.netty.channel.ChannelFutureListener;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.cache.RedisCache;
//import org.springframework.stereotype.Service;
//
//import javax.validation.constraints.NotNull;
//import java.text.MessageFormat;
//import java.util.Objects;
//
///**
// * @Author: hzz
// * @Date: 2022/2/23 15:20:10
// * @Description: client ack 消息
// */
//@Slf4j
//@Service
//public class ClientReceivedMsgAckStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {
//
//    private static final String TAG = "[客户端ack消息]_";
//    private static final String RECEIVE_URL = "http://{0}:{1}/msg/transferAckMsg";
//
//    @Autowired
//    private ObjectMapper objectMapper;
//    @Autowired
//    private RedisCache redisCache;
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
//        return Objects.nonNull(msgType) &&
//                msgType.getFirstLevelMsgType() == MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode()
//                && (MsgTypeEnum.SecondLevelMsgType.UN_READ.getCode() == msgType.getSecondLevelMsgType()
//                || MsgTypeEnum.SecondLevelMsgType.READ.getCode() == msgType.getSecondLevelMsgType());
//    }
//
//    /**
//     * 根据不同类型适配不同的消息格式
//     *
//     * @param msgBaseRequest
//     * @return
//     */
//    private ClientReceivedMsgAckDTO supportPojo(MsgBaseRequest msgBaseRequest) {
//        ClientReceivedMsgAckDTO packet = objectMapper.convertValue(msgBaseRequest.getBody(), ClientReceivedMsgAckDTO.class);
//        packet.setMsgType(msgBaseRequest.getMsgType());
//        return packet;
//    }
//
//
//    @Override
//    public void exchange(ChannelHandlerContext ctx, MsgBaseRequest msgBaseRequest) {
//        log.info((TAG + "exchange_method_start."));
//
//        ClientReceivedMsgAckDTO packet = this.supportPojo(msgBaseRequest);
//        //1. 修改数据库中消息的状态，并push消息至sender
//        int updatedCount = msgService.updateMultiMessageData(getConvertMsgDataDTO(packet));
//        if (updatedCount > 0 && updatedCount == packet.getMsgIds().size()) {
//            // push
//            ReceiveUserDataDTO receiveUserDataTemplate = super.getReceiveUserDataTemplate(packet.getToUserId(), SpringBeanFactory.getBean(RedisCache.class));
//            receiveUserDataTemplate.setToUserId(packet.getToUserId());
//            this.pushSenderClientAckMsg(receiveUserDataTemplate, packet);
//        } else {
//            log.info("更新条数为0或更新了部分消息,updatedCount:{},msgIds:{}", updatedCount, JsonUtil.toJson(packet.getMsgIds()));
//        }
//        log.info((TAG + "exchange_method_end."));
//    }
//
//    /**
//     * 推送ack信息给发送者
//     *
//     * @param receiveUserDataDTO
//     * @param packet
//     */
//    public void pushSenderClientAckMsg(ReceiveUserDataDTO receiveUserDataDTO, ClientReceivedMsgAckDTO packet) {
//        String channelIdByUserId = receiveUserDataDTO.getChannelIdByUserId();
//        Channel targetChannel = receiveUserDataDTO.getTargetChannel();
//        String serverJson = receiveUserDataDTO.getServerJson();
//        String userStatus = receiveUserDataDTO.getUserStatus();
//        ServerInfoDTO serverInfoDTO = receiveUserDataDTO.getServerInfoDTO();
//        log.info((TAG + "接收者id:{},在线状态:{},channelId:{},serverInfo:{}"), packet.getToUserId(), userStatus, channelIdByUserId, serverInfoDTO);
//
//        if (null != targetChannel && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus)) {
//            // 直接发送
//            log.info((TAG + "用户{}在线且在本台机器上,将直接发送"), packet.getToUserId());
//            MsgBaseResponse msgBaseRequest = MsgBaseResponse.buildPushToClientData(packet.getMsgType(), packet, null);
//            ChannelFuture future = targetChannel.writeAndFlush(new TextWebSocketFrame(JsonUtil.toJson(msgBaseRequest)));
//            future.addListener((ChannelFutureListener) channelFuture ->
//                    log.info((TAG + "接收者在线直接发送,消息结果:{}"), channelFuture.isDone()));
//        } else if (Objects.isNull(targetChannel) && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus)
//                && !StringUtil.isNullOrBlank(serverJson)) {
//            log.info((TAG + "用户{}在线但是不在该机器上,跳转到用户所在的服务器,服务器信息:{}"), packet.getToUserId(), serverJson);
//            String requestUrl = MessageFormat.format(RECEIVE_URL, serverInfoDTO.getAddr(), String.valueOf(serverInfoDTO.getPort()));
//            String reqMsg = JsonUtil.toJson(packet);
//            String response = HttpComponet.doPost(requestUrl, reqMsg);
//            log.info((TAG + "transfer_response:{}"), response);
//        }
//    }
//
//    /**
//     * push ack 消息
//     *
//     * @param msgBaseRequest
//     * @return
//     */
//    @Override
//    public BaseResponse receiveAndSendMsg(MsgBaseRequest msgBaseRequest) {
//        log.info((TAG + "receiveAndSendMsg_method_start."));
//
//        ClientReceivedMsgAckDTO packet = supportPojo(msgBaseRequest);
//        String channelIdByUserId = ChannelManager.getChannelIdByUserId(String.valueOf(packet.getToUserId()));
//        Channel targetChannel = ChannelManager.findChannel(channelIdByUserId);
//
//        String userStatus = redisCache.get(UserRedisConstant.LOGIN_STATUS_PREFIX + channelIdByUserId);
//        //二次校验接收人在线状态
//        if (!StringUtil.isNullOrBlank(userStatus) && null != targetChannel) {
//            log.info((TAG + "跳转后用户{}在线,将直接发送消息"), packet.getToUserId());
//            ChannelFuture future = targetChannel.writeAndFlush(new TextWebSocketFrame(JsonUtil.toJson(msgBaseRequest)));
//            future.addListener((ChannelFutureListener) channelFuture ->
//                    log.info((TAG + "接收者在线直接发送,消息结果:{}"), channelFuture.isDone()));
//        }
//        log.info((TAG + "receiveAndSendMsg_method_end."));
//        return BaseResponse.returnResultSuccess("跳转消息成功");
//    }
//
//    /**
//     * 构建消息保存时候的实体
//     *
//     * @param clientReceivedMsgAckDTO
//     * @return
//     */
//    private MessageInfoDTO getConvertMsgDataDTO(ClientReceivedMsgAckDTO clientReceivedMsgAckDTO) {
//        return MessageInfoDTO.builder()
//                .sessionId(clientReceivedMsgAckDTO.getSessionId())
//                .msgIds(clientReceivedMsgAckDTO.getMsgIds())
//                .fromUserId(clientReceivedMsgAckDTO.getFromUserId())
//                .toUserId(clientReceivedMsgAckDTO.getToUserId())
//                .readStatus(clientReceivedMsgAckDTO.getReadStatus())
//                .sendStatus(clientReceivedMsgAckDTO.getSendStatus())
//                .isOffline(MsgStatusEnum.MsgOfflineStatus.NO.getCode())
//                .updateTime(System.currentTimeMillis())
//                .build();
//    }
//}
