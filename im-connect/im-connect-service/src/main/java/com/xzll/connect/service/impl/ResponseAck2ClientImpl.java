package com.xzll.connect.service.impl;


import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.*;
import com.xzll.common.pojo.base.BaseMsgRequestDTO;
import com.xzll.connect.api.ResponseAck2ClientApi;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.connect.pojo.response.dto.C2CClientReceivedMsgAckDTO;
import com.xzll.connect.pojo.response.dto.C2CServerReceivedMsgAckDTO;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/5/30 16:04:34
 * @Description:
 */
@DubboService
@Service
@Slf4j
public class ResponseAck2ClientImpl implements ResponseAck2ClientApi {


    @Override
    public BaseResponse responseServerAck2Client(BaseMsgRequestDTO packet) {
        Assert.isTrue(Objects.nonNull(packet), "参数错误");
        C2CServerResponseAckDTO ackDTO = (C2CServerResponseAckDTO) packet;
        Assert.isTrue(Objects.nonNull(ackDTO) && StringUtils.isNotBlank(ackDTO.getToUserId()), "发送服务端ack时缺少必填参数");
        // 构建&响应服务端是否接收成功消息
        C2CServerReceivedMsgAckDTO c2CServerReceivedMsgAckDTO = getServerReceivedMsgAckVO(packet);
        MsgBaseResponse<C2CServerReceivedMsgAckDTO> msgBaseResponse = MsgBaseResponse.buildPushToClientData(c2CServerReceivedMsgAckDTO.getMsgType(), c2CServerReceivedMsgAckDTO);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(ackDTO.getToUserId());
        boolean result = this.responseAckToSender(targetChannel, msgBaseResponse);
        AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
        return BaseResponse.setResult(resultAnswer);
    }

    @Override
    public BaseResponse responseClientAck2Client(BaseMsgRequestDTO packet) {
        Assert.isTrue(Objects.nonNull(packet), "参数错误");
        ClientReceivedMsgAckDTO ackDTO = (ClientReceivedMsgAckDTO) packet;
        Assert.isTrue(Objects.nonNull(ackDTO) && StringUtils.isNotBlank(ackDTO.getToUserId()), "发送客户端ack时缺少必填参数");
        //构建&响应 消息接收方客户端是否接收成功消息
        C2CClientReceivedMsgAckDTO clientReceivedMsgAckVO = getClientReceivedMsgAckVO(ackDTO);
        MsgBaseResponse<C2CServerReceivedMsgAckDTO> msgBaseResponse = MsgBaseResponse.buildPushToClientData(clientReceivedMsgAckVO.getMsgType(), clientReceivedMsgAckVO);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(ackDTO.getToUserId());
        boolean result = this.responseAckToSender(targetChannel, msgBaseResponse);
        AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
        return BaseResponse.setResult(resultAnswer);
    }

    /**
     * 构建服务端ack 请求体
     *
     * @param packet
     * @param <T>
     * @return
     */
    public static <T extends BaseMsgRequestDTO> C2CServerReceivedMsgAckDTO getServerReceivedMsgAckVO(T packet) {
        C2CServerReceivedMsgAckDTO c2CServerReceivedMsgAckDTO = new C2CServerReceivedMsgAckDTO();
        MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.SERVER_RECEIVE_ACK.getCode());
        c2CServerReceivedMsgAckDTO.setAckTextDesc(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getDesc())
                .setMsgReceivedStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                .setMsgType(msgType);
        c2CServerReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return c2CServerReceivedMsgAckDTO;
    }

    public static C2CClientReceivedMsgAckDTO getClientReceivedMsgAckVO(ClientReceivedMsgAckDTO packet) {
        C2CClientReceivedMsgAckDTO clientReceivedMsgAckDTO = new C2CClientReceivedMsgAckDTO();
        MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        int secondLevelMsgType = 0;
        if (Objects.equals(packet.getMsgStatus(), MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
            secondLevelMsgType = MsgTypeEnum.SecondLevelMsgType.UN_READ.getCode();
        }
        if (Objects.equals(packet.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode())) {
            secondLevelMsgType = MsgTypeEnum.SecondLevelMsgType.READ.getCode();
        }
        msgType.setSecondLevelMsgType(secondLevelMsgType);
        clientReceivedMsgAckDTO.setAckTextDesc(MsgStatusEnum.MsgStatus.getNameByCode(packet.getMsgStatus()))
                .setMsgReceivedStatus(packet.getMsgStatus())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                .setMsgType(msgType);
        clientReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return clientReceivedMsgAckDTO;
    }


    /**
     * 响应 服务端ack/接收方ack 到发送方
     *
     * @param channel
     * @param packet
     */
    public boolean responseAckToSender(Channel channel, MsgBaseResponse packet) {
        try {
            if (Objects.nonNull(channel)) {
                channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(packet)));
                return true;
            }
            log.error("服务端发送ack_传入的channel为空，不发送!");
        } catch (Exception e) {
            log.error("服务端发送ack_异常:", e);
        }
        return false;
    }


}
