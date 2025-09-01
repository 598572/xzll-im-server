package com.xzll.common.grpc;

import com.xzll.common.pojo.response.base.CommonMsgVO;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CWithdrawMsgVO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC消息服务接口 - 优雅的API设计
 */
public interface GrpcMessageService {
    
    /**
     * 发送服务端ACK到客户端
     */
    CompletableFuture<Boolean> sendServerAck(C2CServerReceivedMsgAckVO ackVO);
    
    /**
     * 发送客户端ACK到客户端
     */
    CompletableFuture<Boolean> sendClientAck(C2CClientReceivedMsgAckVO ackVO);
    
    /**
     * 发送撤回消息到客户端
     */
    CompletableFuture<Boolean> sendWithdrawMsg(C2CWithdrawMsgVO withdrawMsgVO);
    
    /**
     * 批量发送消息到多个用户
     */
    CompletableFuture<BatchSendResult> batchSendToUsers(List<String> userIds, CommonMsgVO message, String messageType);
    
    /**
     * 发送消息到指定用户（同步方式）
     */
    boolean sendToUserSync(String userId, CommonMsgVO message, String messageType);
    
    /**
     * 发送消息到指定用户（异步方式）
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
        private final List<UserResult> userResults;
        
        public BatchSendResult(int totalCount, int successCount, int failureCount, List<UserResult> userResults) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.userResults = userResults;
        }
        
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<UserResult> getUserResults() { return userResults; }
        public double getSuccessRate() { 
            return totalCount > 0 ? (double) successCount / totalCount : 0.0; 
        }
    }
    
    /**
     * 单个用户发送结果
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
        
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessRequests() { return successRequests; }
        public long getFailureRequests() { return failureRequests; }
        public int getActiveConnections() { return activeConnections; }
        public double getSuccessRate() { return successRate; }
        public long getAverageResponseTime() { return averageResponseTime; }
    }
} 