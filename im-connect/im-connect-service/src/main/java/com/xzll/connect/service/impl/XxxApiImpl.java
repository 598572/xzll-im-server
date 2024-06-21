package com.xzll.connect.service.impl;


import com.xzll.business.api.RpcTestApi;
import com.xzll.connect.rpcapi.XxxApi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * @Author: hzz
 * @Date: 2024/5/30 12:05:02
 * @Description:
 */
@Service
@DubboService
public class XxxApiImpl implements XxxApi {

    @DubboReference
    private RpcTestApi rpcTestApi;

    @Override
    public String testApi() {
        System.out.println("我是连接服务");
        String test = rpcTestApi.test();
        return test;
    }
}
