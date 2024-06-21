package com.xzll.connect.service.impl;


import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.connect.rpcapi.TransferC2CMsgApi;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/5/30 16:04:34
 * @Description:
 */
@DubboService
@Service
@Slf4j
public class TransferC2CMsgServiceImpl implements TransferC2CMsgApi {

    @Resource
    private HandlerDispatcher handlerDispatcher;

    @Override
    public WebBaseResponse transferC2CMsg(ImBaseRequest imBaseRequest) {
        return handlerDispatcher.receiveAndSendMsg(imBaseRequest);
    }
}
