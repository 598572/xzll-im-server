package com.xzll.connect.service.impl;


import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import com.xzll.connect.service.TransferC2CMsgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/5/30 16:04:34
 * @Description: 消息转发
 */
@Service
@Slf4j
public class TransferC2CMsgServiceImpl implements TransferC2CMsgService {

    @Resource
    private HandlerDispatcher handlerDispatcher;

    @Override
    public WebBaseResponse transferC2CMsg(ImBaseRequest imBaseRequest) {
        return handlerDispatcher.receiveAndSendMsg(imBaseRequest);
    }
}
