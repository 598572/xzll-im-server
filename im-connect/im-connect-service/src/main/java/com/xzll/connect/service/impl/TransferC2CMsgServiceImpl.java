package com.xzll.connect.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.BaseResponse;
import com.xzll.connect.api.TransferC2CMsgApi;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import com.xzll.connect.netty.channel.ChannelManager;

import com.xzll.connect.pojo.constant.UserRedisConstant;
import com.xzll.connect.pojo.dto.C2CMsgRequestDTO;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/5/30 16:04:34
 * @Description:
 */
@DubboService
@org.springframework.stereotype.Service
@Slf4j
public class TransferC2CMsgServiceImpl implements TransferC2CMsgApi {

    private static final String TAG = "[客户端发送单聊消息]_";
    @Resource
    private HandlerDispatcher handlerDispatcher;

    @Override
    public BaseResponse transferC2CMsg(MsgBaseRequest msgBaseRequest) {
        return handlerDispatcher.receiveAndSendMsg(msgBaseRequest);
    }
}
