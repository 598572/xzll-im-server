package com.xzll.business.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;


import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @Auther: Huangzhuangzhuang
 * @Date: 2021/5/22 18:10
 * @Description:
 */
@Slf4j
@RestController
@RequestMapping("/nacos/config")
public class NacosTestController {

	@Value("${timeOutConfig}")
	private Long timeOutConfig;

	@GetMapping("/get")
	public Long get() {
		log.info("测试nacos动态刷新，timeOutConfig：{}",timeOutConfig);
		return timeOutConfig;
	}


}
