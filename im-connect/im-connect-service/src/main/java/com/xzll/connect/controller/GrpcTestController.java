package com.xzll.connect.controller;

import com.xzll.connect.grpc.GrpcPerformanceTest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/grpc/test")
public class GrpcTestController {
    
    @Resource
    private GrpcPerformanceTest performanceTest;
    
    /**
     * 运行性能测试
     */
    @GetMapping("/performance")
    public String runPerformanceTest() {
        log.info("收到性能测试请求");
        
        // 异步运行性能测试，避免阻塞HTTP请求
        new Thread(() -> {
            try {
                performanceTest.runAllPerformanceTests();
            } catch (Exception e) {
                log.error("性能测试执行失败: {}", e.getMessage(), e);
            }
        }).start();
        
        return "性能测试已启动，请查看日志";
    }
    
    /**
     * 运行单次调用测试
     */
    @GetMapping("/single")
    public String runSingleTest() {
        log.info("收到单次调用测试请求");
        
        new Thread(() -> {
            try {
                performanceTest.singleCallPerformanceTest();
            } catch (Exception e) {
                log.error("单次调用测试执行失败: {}", e.getMessage(), e);
            }
        }).start();
        
        return "单次调用测试已启动，请查看日志";
    }
    
    /**
     * 运行并发测试
     */
    @GetMapping("/concurrent")
    public String runConcurrentTest() {
        log.info("收到并发测试请求");
        
        new Thread(() -> {
            try {
                performanceTest.concurrentCallPerformanceTest();
            } catch (Exception e) {
                log.error("并发测试执行失败: {}", e.getMessage(), e);
            }
        }).start();
        
        return "并发测试已启动，请查看日志";
    }
    
    /**
     * 运行批量发送测试
     */
    @GetMapping("/batch")
    public String runBatchTest() {
        log.info("收到批量发送测试请求");
        
        new Thread(() -> {
            try {
                performanceTest.batchSendPerformanceTest();
            } catch (Exception e) {
                log.error("批量发送测试执行失败: {}", e.getMessage(), e);
            }
        }).start();
        
        return "批量发送测试已启动，请查看日志";
    }
} 