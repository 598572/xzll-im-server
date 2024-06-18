package com.xzll.console.controller;


import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.mapper.ImC2CMsgRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


/**
 * @Author: hzz
 * @Date: 2024/6/16 11:05:14
 * @Description: 测试管理后台服务
 */
@Slf4j
@RestController
@RefreshScope
@RequestMapping("/test/console")
public class TestConsoleController {

	@Value("${timeOutConfig}")
	private Long timeOutConfig;
	@Resource
	private ImC2CMsgRecordMapper imC2CMsgRecordMapper;


	@GetMapping("/get")
	public Long get() {
		log.info("测试管理后台服务，nacos timeOutConfig：{}",timeOutConfig);
		LambdaQueryWrapper<ImC2CMsgRecord> msgRecord = Wrappers.lambdaQuery(ImC2CMsgRecord.class);
		List<ImC2CMsgRecord> imC2CMsgRecords = imC2CMsgRecordMapper.selectList(msgRecord);
		log.info("测试orm结果:{}", JSONUtil.toJsonStr(imC2CMsgRecords));
		return timeOutConfig;
	}

}
