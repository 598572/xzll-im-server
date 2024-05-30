package com.xzll.business.rpcimpl;


import com.xzll.business.api.RpcTestApi;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * @Author: hzz
 * @Date: 2022/11/22 15:11:39
 * @Description:
 */
@DubboService
@org.springframework.stereotype.Service
public class RpcTestServiceImpl implements RpcTestApi{

    @Override
    public String test() {
        System.out.println("我是服务提供方");
        return "Rpc通了";
    }
}
