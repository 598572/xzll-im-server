package com.xzll.connect.controller;


import com.alibaba.dubbo.config.annotation.Reference;
import com.xzll.business.api.RpcTestApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2022/11/22 15:28:57
 * @Description:
 */

@RestController
@RequestMapping("/xzll/client")
public class TestController {



    private static final Logger logger = LoggerFactory.getLogger(TestController.class);


    @Reference
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
