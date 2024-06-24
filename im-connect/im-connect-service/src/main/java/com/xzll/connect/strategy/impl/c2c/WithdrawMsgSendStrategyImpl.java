package com.xzll.connect.strategy.impl.c2c;


import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;


/**
 * @Author: hzz
 * @Date: 2024/6/16 12:03:27
 * @Description: 撤回消息发送策略
 */
@Slf4j
@Service
public class WithdrawMsgSendStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {


    private static final String TAG = "[客户端发送撤回消息]_";

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
        return ImBaseRequest.checkSupport(msgType, MsgTypeEnum.FirstLevelMsgType.COMMAND_MSG.getCode(), MsgTypeEnum.SecondLevelMsgType.WITHDRAW.getCode());
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param msgBaseRequest
     * @return
     */
    public C2CWithdrawMsgAO supportPojo(ImBaseRequest msgBaseRequest) {
        C2CWithdrawMsgAO packet = objectMapper.convertValue(msgBaseRequest.getBody(), C2CWithdrawMsgAO.class);
        packet.setMsgType(msgBaseRequest.getMsgType());
        return packet;
    }

    @Override
    public void exchange(ChannelHandlerContext ctx, ImBaseRequest msgBaseRequest) {
        log.info((TAG + "exchange_method_start."));

        //1. 适配对象
        C2CWithdrawMsgAO c2CWithdrawMsgAo = this.supportPojo(msgBaseRequest);
        Assert.isTrue(Objects.nonNull(c2CWithdrawMsgAo) && StringUtils.isNotBlank(c2CWithdrawMsgAo.getToUserId()) && StringUtils.isNotBlank(c2CWithdrawMsgAo.getMsgId()), "发送撤回消息时缺少必填参数");
        //1. 修改数据库中消息的撤回状态，并push消息至sender，此处：修改db与发撤回消息为同步。设计原则：要么第一步存消息就失败，要么：消息新增成功后，后边的状态流转一定要正确所以需要同步
        c2CMsgProvider.sendWithdrawMsg(c2CWithdrawMsgAo);
        log.debug("客户端ack消息_结束");
    }
}
