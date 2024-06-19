package com.xzll.gateway.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/10 17:04:57
 * @Description:
 */
@Slf4j
@RestController
@RequestMapping("/xzll/im")
public class LoginController {

    @PostMapping(value = "/login")
    public WebBaseResponse<Map<String, Object>> test(@RequestBody Map<String,Object> param) {
        log.info("我是网关服务,入参:{}", JsonUtils.toJsonStr(param));
        Map<String, Object> map = new HashMap<>();
        map.put("id",123);
        map.put("name","张三");
        return WebBaseResponse.returnResultSuccess(map);
    }
}
