package com.xzll.connect.service.impl;


import com.xzll.common.pojo.BaseResponse;
import com.xzll.connect.api.TransferC2CMsgApi;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
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

    @Resource
    private HandlerDispatcher handlerDispatcher;

    @Override
    public BaseResponse transferC2CMsg(MsgBaseRequest msgBaseRequest) {
        return handlerDispatcher.receiveAndSendMsg(msgBaseRequest);
    }
}
