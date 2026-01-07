package com.xzll.console.controller;


import cn.hutool.json.JSONUtil;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.service.ImC2CMsgRecordHBaseService;
import com.xzll.console.util.HBaseHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xzll.common.constant.ImConstant.TableConstant.IM_C2C_MSG_RECORD;


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
	private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;
	
	@Resource
	private HBaseHealthChecker hBaseHealthChecker;


	@GetMapping("/get")
	public List<ImC2CMsgRecord> get() {
		log.info("测试管理后台服务，nacos timeOutConfig：{}",timeOutConfig);
		List<ImC2CMsgRecord> imC2CMsgRecords = imC2CMsgRecordHBaseService.getAllMessages();
		log.info("测试HBase查询结果:{}", JSONUtil.toJsonStr(imC2CMsgRecords));
		return imC2CMsgRecords;
	}

	/**
	 * HBase连接健康检查接口
	 */
	@GetMapping("/hbase/health")
	public Map<String, Object> checkHBaseHealth() {
		Map<String, Object> result = new HashMap<>();
		
		try {
			boolean isHealthy = hBaseHealthChecker.isConnectionHealthy();
			String status = hBaseHealthChecker.getConnectionStatus();
			boolean tableExists = hBaseHealthChecker.isTableExists(IM_C2C_MSG_RECORD);
			
			result.put("success", true);
			result.put("hbaseHealthy", isHealthy);
			result.put("connectionStatus", status);
			result.put("tableExists", tableExists);
			result.put("timestamp", System.currentTimeMillis());
			
			if (!isHealthy) {
				result.put("message", "HBase连接异常，请检查配置");
			} else {
				result.put("message", "HBase连接正常");
			}
			
		} catch (Exception e) {
			log.error("HBase健康检查失败", e);
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
