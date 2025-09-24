package com.xzll.connect.grpc;

import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CWithdrawMsgVO;
import com.xzll.common.pojo.response.base.CommonMsgVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC消息服务接口 - 供外部调用
 */
public interface GrpcMessageService {
    
    /**
     * 发送服务端ACK消息
     */
    CompletableFuture<Boolean> sendServerAck(C2CServerReceivedMsgAckVO ackVo);
    
    /**
     * 发送客户端ACK消息
     */
    CompletableFuture<Boolean> sendClientAck(C2CClientReceivedMsgAckVO ackVo);
    
    /**
     * 发送撤回消息
     */
    CompletableFuture<Boolean> sendWithdrawMsg(C2CWithdrawMsgVO withdrawMsgVo);
    
    /**
     * 批量发送消息给多个用户
     */
    CompletableFuture<BatchSendResult> batchSendToUsers(List<String> userIds, CommonMsgVO message, String messageType);
    
    /**
     * 同步发送消息给单个用户
     */
    boolean sendToUserSync(String userId, CommonMsgVO message, String messageType);
    
    /**
     * 异步发送消息给单个用户
     */
    CompletableFuture<Boolean> sendToUserAsync(String userId, CommonMsgVO message, String messageType);
    
    /**
     * 获取服务统计信息
     */
    ServiceStats getServiceStats();
    
    /**
     * 批量发送结果
     */
    class BatchSendResult {
        private final int totalCount;
        private final int successCount;
        private final int failureCount;
        private final List<UserResult> results;
        
        public BatchSendResult(int totalCount, int successCount, int failureCount, List<UserResult> results) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.results = results;
        }
        
        // getters
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<UserResult> getResults() { return results; }
    }
    
    /**
     * 用户发送结果
     */
    class UserResult {
        private final String userId;
        private final boolean success;
        private final String message;
        private final String error;
        
        public UserResult(String userId, boolean success, String message, String error) {
            this.userId = userId;
            this.success = success;
            this.message = message;
            this.error = error;
        }
        
        // getters
        public String getUserId() { return userId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getError() { return error; }
    }
    
    /**
     * 服务统计信息
     */
    class ServiceStats {
        private final long totalRequests;
        private final long successRequests;
        private final long failureRequests;
        private final int activeConnections;
        private final double successRate;
        private final long averageResponseTime;
        
        public ServiceStats(long totalRequests, long successRequests, long failureRequests, 
                          int activeConnections, double successRate, long averageResponseTime) {
            this.totalRequests = totalRequests;
            this.successRequests = successRequests;
            this.failureRequests = failureRequests;
            this.activeConnections = activeConnections;
            this.successRate = successRate;
            this.averageResponseTime = averageResponseTime;
        }
        
        // getters
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessRequests() { return successRequests; }
        public long getFailureRequests() { return failureRequests; }
        public int getActiveConnections() { return activeConnections; }
        public double getSuccessRate() { return successRate; }
        public long getAverageResponseTime() { return averageResponseTime; }
    }
}