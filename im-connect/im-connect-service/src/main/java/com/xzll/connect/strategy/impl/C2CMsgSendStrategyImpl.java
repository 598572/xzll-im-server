package com.xzll.connect.strategy.impl;


import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.pojo.BaseResponse;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.common.util.msgId.MsgIdUtilsService;
import com.xzll.connect.api.TransferC2CMsgApi;
import com.xzll.common.pojo.MsgBaseRequest;


import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.common.constant.UserRedisConstant;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import com.xzll.connect.pojo.dto.MessageInfoDTO;
import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
import com.xzll.connect.pojo.dto.ServerInfoDTO;
import com.xzll.connect.pojo.enums.MsgStatusEnum;
import com.xzll.connect.pojo.enums.MsgTypeEnum;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;

import org.apache.dubbo.rpc.cluster.specifyaddress.Address;
import org.apache.dubbo.rpc.cluster.specifyaddress.UserSpecifiedAddressUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2022/1/14 15:20:10
 * @Description: 单聊消息
 */
@Slf4j
@Service
public class C2CMsgSendStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {

    private static final String TAG = "[客户端发送单聊消息]_";

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @DubboReference(parameters = {"router", "address"})
    private TransferC2CMsgApi transferC2CMsgApi;
    @Resource
    private C2CMsgProvider c2CMsgProvider;
    @Resource
    private MsgIdUtilsService msgIdUtilsService;

    /**
     * 策略适配
     *
     * @param msgType
     * @return
     */
    @Override
    public boolean support( MsgBaseRequest.MsgType msgType) {
        return MsgBaseRequest.checkSupport(msgType, MsgTypeEnum.FirstLevelMsgType.CHAT_MSG.getCode(), MsgTypeEnum.SecondLevelMsgType.C2C.getCode());
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param msgBaseRequest
     * @return
     */
    public C2CMsgRequestDTO supportPojo(MsgBaseRequest msgBaseRequest) {
        C2CMsgRequestDTO packet = objectMapper.convertValue(msgBaseRequest.getBody(), C2CMsgRequestDTO.class);
        //以服务器时间为准
        packet.setMsgCreateTime(System.currentTimeMillis());
        packet.setMsgType(msgBaseRequest.getMsgType());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, MsgBaseRequest msgBaseRequest) {

        log.info((TAG + "exchange_method_start."));

        //消息id可能需要客户端传过来,因为有重试以及消息到达的顺序问题。但是id生成可以是基于长连接请求服务端，服务端来生成msgId
        String msgId = msgIdUtilsService.generateMessageId(123, false);

        C2CMsgRequestDTO packet = this.supportPojo(msgBaseRequest);

        //1. 更新会话记录并保存消息记录（此处是消息表新增的唯一入口,将使用mq方式削峰解耦，避免大量写请求直接打到mysql）
        c2CMsgProvider.addC2CMsg(packet);

        //2. 获取接收人登录，服务信息，根据状态进行处理
        ReceiveUserDataDTO receiveUserData = super.getReceiveUserDataTemplate(packet.getToUserId(), this.redisTemplate);

        String channelIdByUserId = receiveUserData.getChannelIdByUserId();
        Channel targetChannel = receiveUserData.getTargetChannel();
        String ipPortStr = receiveUserData.getRouteAddress();
        String userStatus = receiveUserData.getUserStatus();
        ServerInfoDTO serverInfoDTO = receiveUserData.getServerInfoDTO();
        log.info((TAG + "接收者id:{},在线状态:{},channelId:{},serverInfo:{}"), packet.getToUserId(), userStatus, channelIdByUserId, serverInfoDTO);

        //3. 根据接收人状态做对应的处理
        if (null != targetChannel && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.getValue().toString(), userStatus)) {
            // 直接发送
            log.info((TAG + "用户{}在线且在本台机器上,将直接发送"), packet.getToUserId());
            super.msgSendTemplate(TAG, targetChannel, JSONUtil.toJsonStr(msgBaseRequest));
        } else if (null == userStatus && null == targetChannel) {
            log.info((TAG + "用户{}不在线，将消息保存至离线表中"), packet.getToUserId());
            // 更新消息状态为离线
            this.updateC2cMsgStatus(packet);
        } else if (Objects.isNull(targetChannel) && Objects.equals(UserRedisConstant.UserStatus.ON_LINE.toString(), userStatus)
                && StringUtils.isNotBlank(ipPortStr)) {
            log.info((TAG + "用户{}在线但是不在该机器上,跳转到用户所在的服务器,服务器信息:{}"), packet.getToUserId(), ipPortStr);

            // 根据provider的ip,port创建Address实例并调用
            //dubbo 2.7.13 使用此方式指定 ip:port 调用
            //Address address = new Address(NettyAttrUtil.getIpStr(ipPortStr), NettyAttrUtil.getPortInt(ipPortStr));
            //RpcContext.getContext().setObjectAttachment("address", address);

            //dubbo 3.x 使用此方式指定 ip:port 调用 官方建议：[必须每次都设置，而且设置后必须马上发起调用]
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPortStr), NettyAttrUtil.getPortInt(ipPortStr), false));
            transferC2CMsgApi.transferC2CMsg(msgBaseRequest);
        }
        log.info((TAG + "exchange_method_end."));
    }


    @Override
    public BaseResponse receiveAndSendMsg(MsgBaseRequest msg) {
        log.info((TAG + "receiveAndSendMsg_method_start."));

        C2CMsgRequestDTO packet = supportPojo(msg);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(packet.getToUserId());
        String userStatus = (String) redisTemplate.opsForHash().get(UserRedisConstant.LOGIN_STATUS_PREFIX, packet.getToUserId());
        //二次校验接收人在线状态
        if (StringUtils.isNotBlank(userStatus) && null != targetChannel) {
            log.info((TAG + "跳转后用户{}在线,将直接发送消息"), packet.getToUserId());
            super.msgSendTemplate(TAG, targetChannel, JSONUtil.toJsonStr(msg));
        } else {
            log.info((TAG + "跳转后用户{}不在线,将消息保存至离线表中"), packet.getToUserId());
            this.updateC2cMsgStatus(packet);
        }
        log.info((TAG + "receiveAndSendMsg_method_end."));
        return BaseResponse.returnResultSuccess("跳转消息成功");
    }


    /**
     * 更新消息为离线
     *
     * @param packet
     */
    private void updateC2cMsgStatus(C2CMsgRequestDTO packet) {
        MessageInfoDTO build = MessageInfoDTO.builder()
                .sessionId(packet.getChatId())
                .msgId(packet.getMsgId())
                .msgIds(Collections.singletonList(packet.getMsgId()))
                .fromUserId(packet.getFromUserId())
                .toUserId(packet.getToUserId())
                .isOffline(MsgStatusEnum.MsgOfflineStatus.YES.getCode())
                .updateTime(System.currentTimeMillis())
                .build();
        // 更新消息为离线
//        int updatedCount = msgService.updateMultiMessageData(build);
//        if (updatedCount > 0) {
//            log.info("更新消息为离线,msgId:{},sessionId:{},数量:{}", packet.getMsgId(), packet.getSessionId(), updatedCount);
//        } else {
//            log.info("没有符合条件的消息，msgId:{},sessionId:{}", packet.getMsgId(), packet.getSessionId());
//        }
    }


}
