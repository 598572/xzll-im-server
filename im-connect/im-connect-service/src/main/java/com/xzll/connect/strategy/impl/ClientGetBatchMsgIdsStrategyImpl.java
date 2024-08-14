package com.xzll.connect.strategy.impl;


import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.pojo.response.ClientGetBatchMsgIdVO;
import com.xzll.common.pojo.request.ClientGetMsgIdsAO;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.util.msgId.MsgIdUtilsService;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

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
     * @param baseRequest
     * @return
     */
    @Override
    public boolean support(ImBaseRequest baseRequest) {
        return StringUtils.equals(baseRequest.getUrl(), ImSourceUrlConstant.C2C.GET_BATCH_MSG_ID);
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param imBaseRequest
     * @return
     */
    private ClientGetMsgIdsAO supportPojo(ImBaseRequest imBaseRequest) {
        ClientGetMsgIdsAO packet = objectMapper.convertValue(imBaseRequest.getBody(), ClientGetMsgIdsAO.class);
        packet.setUrl(imBaseRequest.getUrl());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, ImBaseRequest imBaseRequest) {
        log.debug("客户端批量获取消息id_开始");
        ClientGetMsgIdsAO packet = this.supportPojo(imBaseRequest);
        //1. 生成一批消息id
        List<String> msgIds = msgIdUtilsService.generateBatchMessageId(Long.parseLong(packet.getFromUserId()), false);
        ClientGetBatchMsgIdVO rsp = new ClientGetBatchMsgIdVO();
        rsp.setUrl(imBaseRequest.getUrl());
        rsp.setMsgIds(msgIds);
        super.msgSendTemplate(TAG, ctx.channel(), JSONUtil.toJsonStr(rsp));
        log.debug("客户端批量获取消息id_结束");
    }

}
