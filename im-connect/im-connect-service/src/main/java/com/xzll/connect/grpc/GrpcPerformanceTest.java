package com.xzll.connect.grpc;

import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC性能测试类
 */
@Slf4j
@Component
public class GrpcPerformanceTest {
    
    @Resource
    private GrpcMessageService grpcMessageService;
    
    /**
     * 单次调用性能测试
     */
    public void singleCallPerformanceTest() {
        log.info("开始单次调用性能测试...");
        
        C2CServerReceivedMsgAckVO testAck = createTestAck();
        
        // 预热
        for (int i = 0; i < 10; i++) {
            try {
                grpcMessageService.sendServerAck(testAck).get();
            } catch (Exception e) {
                log.warn("预热调用失败: {}", e.getMessage());
            }
        }
        
        // 性能测试
        int testCount = 1000;
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < testCount; i++) {
            long callStart = System.nanoTime();
            try {
                CompletableFuture<Boolean> future = grpcMessageService.sendServerAck(testAck);
                Boolean result = future.get();
                if (result) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.warn("调用失败: {}", e.getMessage());
            }
            long callEnd = System.nanoTime();
            totalTime.addAndGet(callEnd - callStart);
        }
        
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        
        log.info("单次调用性能测试结果:");
        log.info("总调用次数: {}", testCount);
        log.info("成功次数: {}", successCount.get());
        log.info("失败次数: {}", failureCount.get());
        log.info("总耗时: {} ms", totalDuration);
        log.info("平均耗时: {} ms", (double) totalDuration / testCount);
        log.info("平均响应时间: {} μs", (double) totalTime.get() / testCount / 1000);
        log.info("QPS: {}", (double) testCount / totalDuration * 1000);
    }
    
    /**
     * 并发调用性能测试
     */
    public void concurrentCallPerformanceTest() {
        log.info("开始并发调用性能测试...");
        
        int threadCount = 50;
        int callsPerThread = 100;
        int totalCalls = threadCount * callsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalCalls);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < callsPerThread; i++) {
                    long callStart = System.nanoTime();
                    try {
                        C2CServerReceivedMsgAckVO testAck = createTestAck();
                        CompletableFuture<Boolean> future = grpcMessageService.sendServerAck(testAck);
                        Boolean result = future.get();
                        
                        if (result) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.warn("线程 {} 调用失败: {}", threadId, e.getMessage());
                    } finally {
                        long callEnd = System.nanoTime();
                        totalTime.addAndGet(callEnd - callStart);
                        latch.countDown();
                    }
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        
        executor.shutdown();
        
        log.info("并发调用性能测试结果:");
        log.info("线程数: {}", threadCount);
        log.info("每线程调用次数: {}", callsPerThread);
        log.info("总调用次数: {}", totalCalls);
        log.info("成功次数: {}", successCount.get());
        log.info("失败次数: {}", failureCount.get());
        log.info("总耗时: {} ms", totalDuration);
        log.info("平均耗时: {} ms", (double) totalDuration / totalCalls);
        log.info("平均响应时间: {} μs", (double) totalTime.get() / totalCalls / 1000);
        log.info("QPS: {}", (double) totalCalls / totalDuration * 1000);
    }
    
    /**
     * 批量发送性能测试
     */
    public void batchSendPerformanceTest() {
        log.info("开始批量发送性能测试...");
        
        List<String> userIds = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            userIds.add("test_user_" + i);
        }
        
        C2CServerReceivedMsgAckVO testMessage = createTestAck();
        
        // 预热
        try {
            grpcMessageService.batchSendToUsers(userIds, testMessage, "SERVER_ACK").get();
        } catch (Exception e) {
            log.warn("批量发送预热失败: {}", e.getMessage());
        }
        
        // 性能测试
        int testCount = 100;
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < testCount; i++) {
            long callStart = System.nanoTime();
            try {
                CompletableFuture<GrpcMessageService.BatchSendResult> future = 
                    grpcMessageService.batchSendToUsers(userIds, testMessage, "SERVER_ACK");
                GrpcMessageService.BatchSendResult result = future.get();
                
                if (result.getSuccessCount() > 0) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.warn("批量发送失败: {}", e.getMessage());
            }
            long callEnd = System.nanoTime();
            totalTime.addAndGet(callEnd - callStart);
        }
        
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        
        log.info("批量发送性能测试结果:");
        log.info("总调用次数: {}", testCount);
        log.info("成功次数: {}", successCount.get());
        log.info("失败次数: {}", failureCount.get());
        log.info("总耗时: {} ms", totalDuration);
        log.info("平均耗时: {} ms", (double) totalDuration / testCount);
        log.info("平均响应时间: {} μs", (double) totalTime.get() / testCount / 1000);
        log.info("QPS: {}", (double) testCount / totalDuration * 1000);
    }
    
    /**
     * 创建测试用的ACK消息
     */
    private C2CServerReceivedMsgAckVO createTestAck() {
        C2CServerReceivedMsgAckVO ack = new C2CServerReceivedMsgAckVO();
        ack.setToUserId("test_user_001");
        // fromUserId 不在此类中，跳过设置
        ack.setMsgId("test_msg_001");
        ack.setChatId("test_chat_001");
        ack.setUrl("/test/ack");
        ack.setAckTextDesc("测试ACK");
        ack.setMsgReceivedStatus(1);
        ack.setReceiveTime(System.currentTimeMillis());
        return ack;
    }
    
    /**
     * 运行所有性能测试
     */
    public void runAllPerformanceTests() {
        log.info("========== 开始gRPC性能测试 ==========");
        
        try {
            singleCallPerformanceTest();
            Thread.sleep(1000);
            
            concurrentCallPerformanceTest();
            Thread.sleep(1000);
            
            batchSendPerformanceTest();
            
        } catch (Exception e) {
            log.error("性能测试执行失败: {}", e.getMessage(), e);
        }
        
        log.info("========== gRPC性能测试完成 ==========");
    }
} 