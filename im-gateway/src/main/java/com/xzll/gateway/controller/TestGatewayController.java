package com.xzll.gateway.controller;



import com.xzll.connect.api.XxxApi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class TestGatewayController {

    private static final Logger logger = LoggerFactory.getLogger(TestGatewayController.class);


    @DubboReference
    private XxxApi xxxApi;

    @PostMapping(value = "/testImClient")
    public String test(@RequestBody Map<String,String> param) {
        System.out.println("我是网关服务");
        String test = xxxApi.testApi();
        logger.info("dubbo api 返回{}---",test);
        return "";
    }
}
