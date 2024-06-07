package com.xzll.gateway.controller;



import com.xzll.connect.api.XxxApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
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

@Slf4j
@RestController
@RequestMapping("/xzll/client")
@RefreshScope
public class TestGatewayController {

    private static final Logger logger = LoggerFactory.getLogger(TestGatewayController.class);

    //测试nacos自动刷新
    @Value(value = "${timeOutConfig}")
    private Long timeOutConfig;

    @DubboReference
    private XxxApi xxxApi;

    @PostMapping(value = "/testImClient")
    public String test(@RequestBody Map<String,String> param) {
        System.out.println("我是网关服务");
        System.out.println("当前timeOutConfig："+timeOutConfig);
        String test = xxxApi.testApi();
        logger.info("dubbo api 返回{}---",test);
        return "";
    }



}
