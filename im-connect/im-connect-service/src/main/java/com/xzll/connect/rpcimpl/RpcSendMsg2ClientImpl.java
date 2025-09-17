package com.xzll.connect.rpcimpl;


import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.response.C2CWithdrawMsgVO;
import com.xzll.common.pojo.response.base.CommonMsgVO;
import com.xzll.connect.rpcapi.RpcSendMsg2ClientApi;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import com.xzll.common.pojo.response.FriendRequestPushVO;
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
public class RpcSendMsg2ClientImpl implements RpcSendMsg2ClientApi {


    @Override
    public WebBaseResponse responseServerAck2Client(CommonMsgVO packet) {
        Assert.isTrue(Objects.nonNull(packet), "参数错误");
        C2CServerReceivedMsgAckVO ackVo = (C2CServerReceivedMsgAckVO) packet;
        Assert.isTrue(Objects.nonNull(ackVo) && StringUtils.isNotBlank(ackVo.getToUserId()), "发送服务端ack时缺少必填参数");
        // 构建&响应服务端是否接收成功消息
        ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(packet.getUrl(), ackVo);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(ackVo.getToUserId());
        boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
        AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
        return WebBaseResponse.setResult(resultAnswer);
    }

    @Override
    public WebBaseResponse responseClientAck2Client(CommonMsgVO packet) {
        Assert.isTrue(Objects.nonNull(packet), "参数错误");
        C2CClientReceivedMsgAckVO ackDTO = (C2CClientReceivedMsgAckVO) packet;
        Assert.isTrue(Objects.nonNull(ackDTO) && StringUtils.isNotBlank(ackDTO.getToUserId()), "发送客户端ack时缺少必填参数");
        //构建&响应 消息接收方客户端是否接收成功消息
        ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(ackDTO.getUrl(), ackDTO);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(ackDTO.getToUserId());
        boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
        AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
        return WebBaseResponse.setResult(resultAnswer);
    }

    @Override
    public WebBaseResponse sendWithdrawMsg2Client(CommonMsgVO packet) {
        Assert.isTrue(Objects.nonNull(packet), "参数错误");
        C2CWithdrawMsgVO withdrawMsgVo = (C2CWithdrawMsgVO) packet;
        //构建&响应 消息接收方客户端是否接收成功消息
        ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(withdrawMsgVo.getUrl(), withdrawMsgVo);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(withdrawMsgVo.getToUserId());
        boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
        AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
        return WebBaseResponse.setResult(resultAnswer);
    }

    @Override
    public WebBaseResponse sendFriendRequestPush2Client(CommonMsgVO packet) {
        Assert.isTrue(Objects.nonNull(packet), "参数错误");
        FriendRequestPushVO friendRequestPushVO = (FriendRequestPushVO) packet;
        Assert.isTrue(Objects.nonNull(friendRequestPushVO) && StringUtils.isNotBlank(friendRequestPushVO.getToUserId()), 
                "发送好友申请推送时缺少必填参数");
        
        //构建推送消息
        ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(friendRequestPushVO.getUrl(), friendRequestPushVO);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(friendRequestPushVO.getToUserId());
        boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
        
        AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
        return WebBaseResponse.setResult(resultAnswer);
    }


    /**
     * 响应 服务端ack/接收方ack 到发送方
     *
     * @param channel
     * @param packet
     */
    public boolean sendMsg2Client(Channel channel, ImBaseResponse packet) {
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
