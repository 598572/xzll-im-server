package com.xzll.common.grpc;

import com.xzll.grpc.ServerAckPush;
import com.xzll.grpc.ClientAckPush;
import com.xzll.grpc.WithdrawPush;
import com.xzll.grpc.FriendRequestPush;
import com.xzll.grpc.FriendResponsePush;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC消息服务接口 - 类型化Push设计
 */
public interface GrpcMessageService {
    
    /**
     * 发送服务端ACK到客户端（下行推送）
     */
    CompletableFuture<Boolean> sendServerAck(ServerAckPush push);
    
    /**
     * 发送客户端ACK到客户端（下行推送）
     */
    CompletableFuture<Boolean> sendClientAck(ClientAckPush push);
    
    /**
     * 发送撤回消息到客户端（下行推送）
     */
    CompletableFuture<Boolean> sendWithdrawMsg(WithdrawPush push);

    /**
     * 发送好友请求推送到客户端（下行推送）
     */
    CompletableFuture<Boolean> pushFriendRequest(FriendRequestPush push);
    
    /**
     * 发送好友响应推送到客户端（下行推送）
     */
    CompletableFuture<Boolean> pushFriendResponse(FriendResponsePush push);
    
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