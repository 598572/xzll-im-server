package com.xzll.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/7 17:04:57
 * @Description:
 */
@Slf4j
@RestController
@RequestMapping("/xzll/im")
@RefreshScope
public class LoginController {

    @PostMapping(value = "/login")
    public String test(@RequestBody Map<String,String> param) {
        System.out.println("我是网关服务");
        return "";
    }
}
