package com.xzll.connect.strategy.impl;


import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.exception.XzllBusinessException;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.LastChatListAO;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.service.TransferC2CMsgService;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2022/1/14 15:20:10
 * @Description: 最近会话列表
 */
@Slf4j
@Service
public class LastChatListStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {

    private static final String TAG = "[最近会话列表]_";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    //需要此注解 否则 C2CMsgSendStrategyImpl 和 TransferC2CMsgService将循环依赖
    @Lazy
    @Resource
    private TransferC2CMsgService transferC2CMsgService;
    @Resource
    private C2CMsgProvider c2CMsgProvider;


    /**
     * 通过url路由不同的策略
     *
     * @param baseRequest
     * @return
     */
    @Override
    public boolean support(ImBaseRequest baseRequest) {
        return ImBaseRequest.checkSupport(ImSourceUrlConstant.LAST_CHAT_LIST, baseRequest.getUrl());
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param imBaseRequest
     * @return
     */
    public LastChatListAO supportPojo(ImBaseRequest imBaseRequest) {
        LastChatListAO packet = objectMapper.convertValue(imBaseRequest.getBody(), LastChatListAO.class);
        packet.setUrl(imBaseRequest.getUrl());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, ImBaseRequest imBaseRequest) {
        LastChatListAO packet = this.supportPojo(imBaseRequest);
        String uid = ctx.channel().attr(ImConstant.USER_ID_KEY).get();

        if (!StringUtils.equals(packet.getUserId(), uid)) {
            log.warn("客户端传入的userId和从连接中获取的userId不一致，存在安全风险，连接关闭");
            ctx.close();
        }
        //todo 从数据库获取最近会话列表

        super.msgSendTemplate(TAG, ctx.channel(), JSONUtil.toJsonStr(null));
    }

    @Override
    public WebBaseResponse<String> receiveAndSendMsg(ImBaseRequest msg) {
        throw new XzllBusinessException(AnswerCode.FUNCTION_UNUSE);
    }
}
