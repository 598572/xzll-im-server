package com.xzll.connect.strategy.impl;


import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.response.ClientGetBatchMsgIdVO;
import com.xzll.common.pojo.request.ClientGetMsgIdsAO;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.util.msgId.MsgIdUtilsService;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/6/16 15:20:10
 * @Description: id发号器， （一次生成多少可配在配置中心）, 因为有重试以及消息到达的顺序问题同时为了统一管理等诸多原因。所以id生成是基于长连接请求服务端，服务端来生成msgId
 */
@Slf4j
@Service
public class ClientGetBatchMsgIdsStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {


    private static final String TAG = "客户端批量获取消息id";

    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private MsgIdUtilsService msgIdUtilsService;

    /**
     * 策略适配
     *
     * @param msgType
     * @return
     */
    @Override
    public boolean support(ImBaseRequest.MsgType msgType) {
        return Objects.nonNull(msgType) && msgType.getFirstLevelMsgType() == MsgTypeEnum.FirstLevelMsgType.GET_DATA_MSG.getCode()
                && MsgTypeEnum.SecondLevelMsgType.GET_MSG_IDS.getCode() == msgType.getSecondLevelMsgType();
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param imBaseRequest
     * @return
     */
    private ClientGetMsgIdsAO supportPojo(ImBaseRequest imBaseRequest) {
        ClientGetMsgIdsAO packet = objectMapper.convertValue(imBaseRequest.getBody(), ClientGetMsgIdsAO.class);
        packet.setMsgType(imBaseRequest.getMsgType());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, ImBaseRequest imBaseRequest) {
        log.debug("客户端批量获取消息id_开始");
        ClientGetMsgIdsAO packet = this.supportPojo(imBaseRequest);
        //1. 生成一批消息id
        List<String> msgIds = msgIdUtilsService.generateBatchMessageId(Long.parseLong(packet.getFromUserId()), false);
        ClientGetBatchMsgIdVO rsp = new ClientGetBatchMsgIdVO();
        ImBaseResponse.MsgType msgType = new ImBaseResponse.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.GET_DATA_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.GET_MSG_IDS.getCode());
        rsp.setMsgType(msgType);
        rsp.setMsgIds(msgIds);
        super.msgSendTemplate(TAG, ctx.channel(), JSONUtil.toJsonStr(rsp));
        log.debug("客户端批量获取消息id_结束");
    }

}
