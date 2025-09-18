package com.xzll.datasync.controller;

import com.xzll.datasync.consumer.BatchDataSyncConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 监控控制器
 * 提供数据同步服务的监控信息
 * 
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
public class MonitorController {
    
    @Resource
    private BatchDataSyncConsumer batchDataSyncConsumer;
    
    /**
     * 获取数据同步统计信息
     */
    @GetMapping("/stats")
    public String getStats() {
        try {
            // 获取批量消费统计信息
            return batchDataSyncConsumer.getStats();
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return "获取统计信息失败: " + e.getMessage();
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
} 