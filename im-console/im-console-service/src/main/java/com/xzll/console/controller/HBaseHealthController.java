package com.xzll.console.controller;

import com.xzll.console.config.HBaseHealthCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * HBase 健康状态检查控制器
 * @Author: hzz
 * @Date: 2025/08/27
 */
@Slf4j
@RestController
@RequestMapping("/hbase/health")
@CrossOrigin
public class HBaseHealthController {

    @Autowired
    private HBaseHealthCheck hbaseHealthCheck;

    /**
     * 检查HBase连接状态
     */
    @GetMapping("/status")
    public Map<String, Object> getHBaseStatus() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean isHealthy = hbaseHealthCheck.isConnectionHealthy();
            String status = hbaseHealthCheck.getConnectionStatus();
            
            result.put("success", true);
            result.put("healthy", isHealthy);
            result.put("status", status);
            result.put("timestamp", System.currentTimeMillis());
            
            if (isHealthy) {
                result.put("message", "HBase连接正常");
            } else {
                result.put("message", "HBase连接异常: " + status);
            }
            
        } catch (Exception e) {
            log.error("检查HBase状态时发生异常", e);
            result.put("success", false);
            result.put("healthy", false);
            result.put("status", "检查失败");
            result.put("message", "检查HBase状态时发生异常: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }

    /**
     * 手动触发HBase连接检查
     */
    @GetMapping("/check")
    public Map<String, Object> checkHBaseConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("手动触发HBase连接检查");
            hbaseHealthCheck.checkHBaseConnection();
            
            boolean isHealthy = hbaseHealthCheck.isConnectionHealthy();
            String status = hbaseHealthCheck.getConnectionStatus();
            
            result.put("success", true);
            result.put("healthy", isHealthy);
            result.put("status", status);
            result.put("message", "HBase连接检查完成");
            result.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("手动检查HBase连接时发生异常", e);
            result.put("success", false);
            result.put("healthy", false);
            result.put("status", "检查失败");
            result.put("message", "手动检查HBase连接时发生异常: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
} 