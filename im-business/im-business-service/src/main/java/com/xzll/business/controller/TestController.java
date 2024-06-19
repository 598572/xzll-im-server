package com.xzll.business.controller;

import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


/**
 * @Auther: Huangzhuangzhuang
 * @Date: 2021/5/22 18:10
 * @Description:
 */
@Slf4j
@RestController
@RequestMapping("/nacos/config")
public class TestController {

	@Value("${timeOutConfig}")
	private Long timeOutConfig;
	@Resource
	private ImC2CMsgRecordService imC2CMsgRecordService;

	@GetMapping("/get")
	public Long get() {
		log.info("测试nacos动态刷新，timeOutConfig：{}",timeOutConfig);
		return timeOutConfig;
	}

	/**
	 * 测试es读写
	 *
	 * @param dto
	 * @return
	 */
	@PostMapping("/testes")
	public Long testes(@RequestBody C2CSendMsgAO dto) {
		imC2CMsgRecordService.testEs(dto);
		return timeOutConfig;
	}


}
