package com.xzll.business.controller;

import com.xzll.business.service.impl.ServerAckSimpleRetryService;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/12/25
 * @Description: ServerAck重试监控控制器 - 纯MQ方案
 */
@Slf4j
@RestController
@RequestMapping("/api/server-ack-simple")
public class ServerAckSimpleMonitorController {
    
    @Resource
    private ServerAckSimpleRetryService serverAckSimpleRetryService;
    
    /**
     * 获取重试统计信息
     */
    @GetMapping("/statistics")
    public WebBaseResponse getRetryStatistics() {
        try {
            ServerAckSimpleRetryService.RetryStatistics stats = 
                serverAckSimpleRetryService.getRetryStatistics();
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalTasks", stats.getTotalTasks());
            result.put("successTasks", stats.getSuccessTasks());
            result.put("failedTasks", stats.getFailedTasks());
            result.put("successRate", String.format("%.2f%%", stats.getSuccessRate() * 100));
            result.put("failureRate", String.format("%.2f%%", stats.getFailureRate() * 100));
            result.put("timestamp", System.currentTimeMillis());
            
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (Exception e) {
            log.error("获取重试统计信息失败", e);
            return WebBaseResponse.returnResultError("获取统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public WebBaseResponse getHealthStatus() {
        try {
            ServerAckSimpleRetryService.RetryStatistics stats = 
                serverAckSimpleRetryService.getRetryStatistics();
            
            // 简单的健康评估：失败率低于10%认为健康
            boolean isHealthy = stats.getFailureRate() < 0.10;
            String healthStatus = isHealthy ? "HEALTHY" : "WARNING";
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", healthStatus);
            result.put("healthy", isHealthy);
            result.put("successRate", String.format("%.1f%%", stats.getSuccessRate() * 100));
            result.put("failureRate", String.format("%.1f%%", stats.getFailureRate() * 100));
            result.put("totalTasks", stats.getTotalTasks());
            
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (Exception e) {
            log.error("获取健康状态失败", e);
            return WebBaseResponse.returnResultError("健康检查失败: " + e.getMessage());
        }
    }
}
