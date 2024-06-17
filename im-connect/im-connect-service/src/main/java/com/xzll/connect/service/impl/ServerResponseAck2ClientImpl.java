package com.xzll.connect.service.impl;


import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.*;
import com.xzll.common.pojo.base.BaseMsgRequestDTO;
import com.xzll.connect.api.ServerResponseAck2ClientApi;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.pojo.enums.MsgStatusEnum;
import com.xzll.connect.pojo.enums.MsgTypeEnum;
import com.xzll.connect.pojo.response.dto.ServerReceivedMsgAckDTO;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/5/30 16:04:34
 * @Description:
 */
@DubboService
@org.springframework.stereotype.Service
@Slf4j
public class ServerResponseAck2ClientImpl implements ServerResponseAck2ClientApi {


    @Override
    public void serverResponseAck2Client(BaseMsgRequestDTO packet) {
        C2CServerAckDTO ackDTO = (C2CServerAckDTO) packet;
        Assert.isTrue(Objects.nonNull(ackDTO) && StringUtils.isNotBlank(ackDTO.getToUserId()), "发送服务端ack时缺少必填参数");
        // 构建&响应服务端是否接收成功消息
        ServerReceivedMsgAckDTO serverReceivedMsgAckDTO = getServerReceivedMsgAckVO(packet);
        MsgBaseResponse<ServerReceivedMsgAckDTO> msgBaseResponse = MsgBaseResponse.buildPushToClientData(packet.getMsgType(), serverReceivedMsgAckDTO);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(ackDTO.getToUserId());
        this.responseServerReceiveAckToSender(targetChannel, msgBaseResponse);
    }

    /**
     * 构建服务端ack 请求体
     *
     * @param packet
     * @param <T>
     * @return
     */
    public static <T extends BaseMsgRequestDTO> ServerReceivedMsgAckDTO getServerReceivedMsgAckVO(T packet) {
        ServerReceivedMsgAckDTO serverReceivedMsgAckDTO = new ServerReceivedMsgAckDTO();
        MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.SERVER_RECEIVE_ACK.getCode());
        serverReceivedMsgAckDTO.setAckTextDesc(MsgStatusEnum.MsgSendStatus.SERVER_RECEIVED.getDesc())
                .setMsgReceivedStatus(MsgStatusEnum.MsgSendStatus.SERVER_RECEIVED.getCode())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                .setMsgType(msgType);
        serverReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return serverReceivedMsgAckDTO;
    }


    /**
     * 服务端接收消息后ack common 4 client
     *
     * @param channel
     * @param packet
     */
    public void responseServerReceiveAckToSender(Channel channel, MsgBaseResponse packet) {
        try {
            if (Objects.nonNull(channel)) {
                channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(packet)));
                return;
            }
            log.error("服务端发送ack_传入的channel为空，不发送!");
        } catch (Exception e) {
            log.error("服务端发送ack_异常:", e);
        }
    }


}
