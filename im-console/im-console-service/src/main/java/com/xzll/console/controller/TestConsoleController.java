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
	public List<ImC2CMsgRecord> get() {
		log.info("测试管理后台服务，nacos timeOutConfig：{}",timeOutConfig);
		LambdaQueryWrapper<ImC2CMsgRecord> msgRecord = Wrappers.lambdaQuery(ImC2CMsgRecord.class);
		List<ImC2CMsgRecord> imC2CMsgRecords = imC2CMsgRecordMapper.selectList(msgRecord);
		log.info("测试orm结果:{}", JSONUtil.toJsonStr(imC2CMsgRecords));
		return imC2CMsgRecords;
	}

	/**
	 * 测试权限绕过接口
	 * 当配置 auth.enable-permission-check=false 时，此接口可以绕过权限验证
	 */
	@GetMapping("/test-permission-bypass")
	public String testPermissionBypass() {
		log.info("测试权限绕过接口被调用");
		return "权限绕过测试成功！当前时间：" + System.currentTimeMillis();
	}

}
