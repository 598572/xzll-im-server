package com.xzll.connect.controller;

import com.xzll.business.api.RpcTestApi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2022/11/22 15:28:57
 * @Description:
 */

@Component
public class TestDubbo {

    private static final Logger logger = LoggerFactory.getLogger(TestDubbo.class);


    @DubboReference
    private RpcTestApi rpcTestApi;

    @PostMapping(value = "/testImClient")
    public String test(@RequestBody Map<String,String> param) {
        System.out.println("哈哈");
        String test = rpcTestApi.test();
        logger.info("打印个日志");
        logger.info("---------{}---",test);
        return "";
    }
}
