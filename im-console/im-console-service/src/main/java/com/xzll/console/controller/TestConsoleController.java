package com.xzll.console.controller;


import cn.hutool.json.JSONUtil;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.service.MessageMongoQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
	private MessageMongoQueryService messageMongoQueryService;


	@GetMapping("/get")
	public List<ImC2CMsgRecord> get() {
		log.info("测试管理后台服务，nacos timeOutConfig：{}",timeOutConfig);
		List<ImC2CMsgRecord> imC2CMsgRecords = messageMongoQueryService.getLatestMessages(20);
		log.info("测试MongoDB查询结果:{}", JSONUtil.toJsonStr(imC2CMsgRecords));
		return imC2CMsgRecords;
	}

	/**
	 * MongoDB连接健康检查接口
	 */
	@GetMapping("/mongo/health")
	public Map<String, Object> checkMongoHealth() {
		Map<String, Object> result = new HashMap<>();
		
		try {
			boolean isHealthy = messageMongoQueryService.isConnectionHealthy();
			
			result.put("success", true);
			result.put("mongoHealthy", isHealthy);
			result.put("timestamp", System.currentTimeMillis());
			
			if (!isHealthy) {
				result.put("message", "MongoDB连接异常，请检查配置");
			} else {
				result.put("message", "MongoDB连接正常");
			}
			
		} catch (Exception e) {
			log.error("MongoDB健康检查失败", e);
			result.put("success", false);
			result.put("message", "健康检查失败: " + e.getMessage());
			result.put("error", e.toString());
		}
		
		return result;
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
