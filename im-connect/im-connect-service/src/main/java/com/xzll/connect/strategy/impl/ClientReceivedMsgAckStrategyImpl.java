package com.xzll.connect.strategy.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/6/16 15:20:10
 * @Description: client ack 消息
 */
@Slf4j
@Service
public class ClientReceivedMsgAckStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {

    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private C2CMsgProvider c2CMsgProvider;

    /**
     * 策略适配
     *
     * @param msgType
     * @return
     */
    @Override
    public boolean support(ImBaseRequest.MsgType msgType) {
        return Objects.nonNull(msgType) &&
                msgType.getFirstLevelMsgType() == MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode()
                && (MsgTypeEnum.SecondLevelMsgType.UN_READ.getCode() == msgType.getSecondLevelMsgType()
                || MsgTypeEnum.SecondLevelMsgType.READ.getCode() == msgType.getSecondLevelMsgType());
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param imBaseRequest
     * @return
     */
    private C2CReceivedMsgAckAO supportPojo(ImBaseRequest imBaseRequest) {
        C2CReceivedMsgAckAO packet = objectMapper.convertValue(imBaseRequest.getBody(), C2CReceivedMsgAckAO.class);
        packet.setMsgType(imBaseRequest.getMsgType());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, ImBaseRequest imBaseRequest) {
        log.debug("客户端ack消息_开始");
        C2CReceivedMsgAckAO packet = this.supportPojo(imBaseRequest);
        //1. 修改数据库中消息的状态，并push消息至sender，此处：修改db与发ack消息为同步。设计原则：要么第一步存消息就失败，要么：消息新增成功后，后边的状态流转一定要正确所以需要同步
        c2CMsgProvider.clientResponseAck(packet);
        log.debug("客户端ack消息_结束");
    }

}
