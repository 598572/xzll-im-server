package com.xzll.connect.strategy.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
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

    private static final String TAG = "[客户端ack消息]_";

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
    public boolean support(MsgBaseRequest.MsgType msgType) {
        return Objects.nonNull(msgType) &&
                msgType.getFirstLevelMsgType() == MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode()
                && (MsgTypeEnum.SecondLevelMsgType.UN_READ.getCode() == msgType.getSecondLevelMsgType()
                || MsgTypeEnum.SecondLevelMsgType.READ.getCode() == msgType.getSecondLevelMsgType());
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param msgBaseRequest
     * @return
     */
    private ClientReceivedMsgAckDTO supportPojo(MsgBaseRequest msgBaseRequest) {
        ClientReceivedMsgAckDTO packet = objectMapper.convertValue(msgBaseRequest.getBody(), ClientReceivedMsgAckDTO.class);
        packet.setMsgType(msgBaseRequest.getMsgType());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, MsgBaseRequest msgBaseRequest) {
        log.info((TAG + "exchange_method_start"));
        ClientReceivedMsgAckDTO packet = this.supportPojo(msgBaseRequest);
        //1. 修改数据库中消息的状态，并push消息至sender
        c2CMsgProvider.clientResponseAck(packet);
        log.info((TAG + "exchange_method_end"));
    }

}
