package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.xzll.business.cluster.mq.RocketMqProducerWrap;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.grpc.ServerAckPush;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author: hzz
 * @Date: 2024/12/25
 * @Description: ServerAck重试服务 - 纯MQ延迟重试
 */
@Slf4j
@Service
public class ServerAckSimpleRetryService {
    
    private static final String TAG = "[ServerAck重试服务]";
    private static final String SERVER_ACK_RETRY_TOPIC = "SERVER_ACK_RETRY_TOPIC";
    
    @Resource
    private GrpcMessageService grpcMessageService;
    
    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;
    
    // 基础配置
    @Value("${im-server.ack.retry.enabled:true}")
    private boolean retryEnabled;
    
    @Value("${im-server.ack.retry.max-retries:3}")
    private int maxRetries;
    
    @Value("${im-server.ack.retry.delays:5,30,300}")
    private String delaysConfig;
    
    @Value("${im-server.ack.retry.alert.enabled:true}")
    private boolean alertEnabled;
    
    @Value("${im-server.ack.retry.alert.webhook.url:}")
    private String alertWebhookUrl;
    
    // 动态配置
    private int[] retryDelays;
    
    // 统计指标
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong successTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    
    /**
     * 初始化配置参数
     */
    @PostConstruct
    public void initConfig() {
        // 解析重试延迟配置
        String[] delayStrs = delaysConfig.split(",");
        retryDelays = new int[delayStrs.length];
        for (int i = 0; i < delayStrs.length; i++) {
            retryDelays[i] = Integer.parseInt(delayStrs[i].trim());
        }
        
        log.info("{}配置初始化完成 - 重试次数: {}, 延迟: {}s", 
            TAG, maxRetries, java.util.Arrays.toString(retryDelays));
    }
    
    /**
     * 发送ServerAck，失败时启动MQ重试机制
     */
    public CompletableFuture<Boolean> sendServerAckWithRetry(ServerAckPush ackPush) {
        if (!retryEnabled) {
            return grpcMessageService.sendServerAck(ackPush);
        }
        
        totalTasks.incrementAndGet();
        
        // 创建一个新的CompletableFuture来控制返回结果
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        grpcMessageService.sendServerAck(ackPush).whenComplete((success, throwable) -> {
            if (throwable != null) {
                // 发生异常，启动MQ重试
                log.error("{}ServerAck发送异常，启动MQ重试 - clientMsgId: {}, error: {}", 
                    TAG, ackPush.getClientMsgId(), throwable.getMessage());
                boolean mqSent = sendToMqRetry(ackPush, 1);
                if (mqSent) {
                    // MQ重试提交成功，向上传递异常
                    resultFuture.completeExceptionally(new RuntimeException("ServerAck发送失败，已启动MQ重试", throwable));
                } else {
                    // MQ重试提交失败
                    resultFuture.completeExceptionally(new RuntimeException("ServerAck发送失败，MQ重试提交也失败", throwable));
                }
            } else if (success != null && success) {
                // 发送成功
                successTasks.incrementAndGet();
                log.info("{}ServerAck发送成功 - clientMsgId: {}", TAG, ackPush.getClientMsgId());
                resultFuture.complete(true);
            } else {
                // 返回false，启动MQ重试
                log.warn("{}ServerAck发送返回false，启动MQ重试 - clientMsgId: {}", TAG, ackPush.getClientMsgId());
                boolean mqSent = sendToMqRetry(ackPush, 1);
                if (mqSent) {
                    // MQ重试提交成功，向上传递失败
                    resultFuture.completeExceptionally(new RuntimeException("ServerAck发送返回false，已启动MQ重试"));
                } else {
                    // MQ重试提交失败
                    resultFuture.completeExceptionally(new RuntimeException("ServerAck发送返回false，MQ重试提交也失败"));
                }
            }
        });
        
        return resultFuture;
    }
    
    /**
     * 发送到MQ进行延迟重试（使用RocketMQ延迟消息功能）
     */
    private boolean sendToMqRetry(ServerAckPush ackPush, int retryCount) {
        try {
            // 将Protobuf对象转换为可序列化的数据对象
            ServerAckRetryEvent retryEvent = new ServerAckRetryEvent();
            retryEvent.setServerAckPush(ServerAckPushData.fromProtobuf(ackPush));
            retryEvent.setRetryCount(retryCount);
            retryEvent.setMaxRetries(maxRetries);
            retryEvent.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.SERVER_ACK_RETRY);
            clusterEvent.setData(JSONUtil.toJsonStr(retryEvent));
            clusterEvent.setCreateTime(new java.util.Date());
            
            // 使用clientMsgId作为balanceId，保证同一客户端消息的重试顺序（bytes -> string）
            String balanceId = ProtoConverterUtil.bytesToUuidString(ackPush.getClientMsgId());
            
            // 获取延迟时间
            int delaySeconds = retryDelays[retryCount - 1];
            
            // 使用RocketMQ延迟消息功能
            boolean sent = rocketMqProducerWrap.sendDelayClusterEvent(
                SERVER_ACK_RETRY_TOPIC, 
                clusterEvent, 
                balanceId, 
                delaySeconds
            );
            
            if (sent) {
                log.info("{}MQ延迟重试任务已提交 - clientMsgId: {}, 第{}次重试, {}s后执行", 
                    TAG, ProtoConverterUtil.bytesToUuidString(ackPush.getClientMsgId()), retryCount, delaySeconds);
            } else {
                log.error("{}MQ延迟重试任务提交失败 - clientMsgId: {}", TAG, ProtoConverterUtil.bytesToUuidString(ackPush.getClientMsgId()));
            }
            
            return sent;
            
        } catch (Exception e) {
            log.error("{}发送MQ延迟重试失败 - clientMsgId: {}, error: {}", 
                TAG, ackPush.getClientMsgId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 处理MQ重试事件（由Consumer调用）
     */
    public void handleMqRetry(ServerAckRetryEvent retryEvent) {
        // 将数据对象转换回Protobuf对象
        ServerAckPush ackPush = retryEvent.getServerAckPush().toProtobuf();
        int currentRetry = retryEvent.getRetryCount();
        
        log.info("{}处理MQ重试 - clientMsgId: {}, 第{}次重试", 
            TAG, ackPush.getClientMsgId(), currentRetry);
        
        grpcMessageService.sendServerAck(ackPush).whenComplete((success, throwable) -> {
            if (success != null && success) {
                successTasks.incrementAndGet();
                log.info("{}MQ重试成功 - clientMsgId: {}, 第{}次重试成功", 
                    TAG, ackPush.getClientMsgId(), currentRetry);
            } else {
                String errorMsg = throwable != null ? throwable.getMessage() : "返回false";
                log.warn("{}MQ重试失败 - clientMsgId: {}, 第{}次重试失败, error: {}", 
                    TAG, ackPush.getClientMsgId(), currentRetry, errorMsg);
                
                if (currentRetry < retryEvent.getMaxRetries()) {
                    // 继续下次重试
                    boolean nextMqSent = sendToMqRetry(ackPush, currentRetry + 1);
                    if (nextMqSent) {
                        log.info("{}已提交下次MQ重试 - clientMsgId: {}, 将进行第{}次重试", 
                            TAG, ackPush.getClientMsgId(), currentRetry + 1);
                    } else {
                        log.error("{}提交下次MQ重试失败 - clientMsgId: {}", TAG, ackPush.getClientMsgId());
                        recordFinalFailure(ackPush);
                    }
                } else {
                    // 最终失败
                    recordFinalFailure(ackPush);
                }
            }
        });
    }
    
    /**
     * 记录最终失败
     */
    private void recordFinalFailure(ServerAckPush ackPush) {
        failedTasks.incrementAndGet();
        log.error("{}ServerAck最终重试失败 - clientMsgId: {}, 已尝试{}次", 
            TAG, ackPush.getClientMsgId(), maxRetries);
        
        // 发送告警
        sendAlert(ackPush, "ServerAck重试全部失败");
    }
    
    /**
     * 发送告警
     */
    private void sendAlert(ServerAckPush ackPush, String reason) {
        if (!alertEnabled) {
            return;
        }
        
        log.error("{}【告警】{} - clientMsgId: {}, msgId: {}, toUserId: {}", 
            TAG, reason, ackPush.getClientMsgId(), ackPush.getMsgId(), ackPush.getToUserId());
        
        // 如果配置了webhook URL，发送告警
        if (alertWebhookUrl != null && !alertWebhookUrl.trim().isEmpty()) {
            try {
                String alertMessage = String.format(
                    "【ServerAck重试失败告警】\n" +
                    "ClientMsgId: %s\n" +
                    "MsgId: %s\n" +
                    "ToUserId: %s\n" +
                    "原因: %s\n" +
                    "时间: %s",
                    ackPush.getClientMsgId(),
                    ackPush.getMsgId(),
                    ackPush.getToUserId(),
                    reason,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
                
                log.info("{}告警消息准备发送到webhook: {} - {}", TAG, alertWebhookUrl, alertMessage);
                
                // TODO: 实现HTTP POST请求发送到webhook
                
            } catch (Exception e) {
                log.error("{}发送告警失败 - webhook: {}, error: {}", TAG, alertWebhookUrl, e.getMessage(), e);
            }
        }
    }
    
    /**
     * 获取统计信息
     */
    public RetryStatistics getRetryStatistics() {
        long total = totalTasks.get();
        long success = successTasks.get();
        long failed = failedTasks.get();
        double successRate = total > 0 ? (double) success / total : 1.0;
        double failureRate = total > 0 ? (double) failed / total : 0.0;
        
        return new RetryStatistics(total, success, failed, successRate, failureRate);
    }
    
    /**
     * 重试统计信息
     */
    public static class RetryStatistics {
        private final long totalTasks;
        private final long successTasks;
        private final long failedTasks;
        private final double successRate;
        private final double failureRate;
        
        public RetryStatistics(long totalTasks, long successTasks, long failedTasks, 
                             double successRate, double failureRate) {
            this.totalTasks = totalTasks;
            this.successTasks = successTasks;
            this.failedTasks = failedTasks;
            this.successRate = successRate;
            this.failureRate = failureRate;
        }
        
        // Getters
        public long getTotalTasks() { return totalTasks; }
        public long getSuccessTasks() { return successTasks; }
        public long getFailedTasks() { return failedTasks; }
        public double getSuccessRate() { return successRate; }
        public double getFailureRate() { return failureRate; }
        
        @Override
        public String toString() {
            return String.format(
                "RetryStatistics{total=%d, success=%d, failed=%d, successRate=%.2f%%, failureRate=%.2f%%}",
                totalTasks, successTasks, failedTasks, successRate * 100, failureRate * 100
            );
        }
    }
    
    /**
     * ServerAck重试事件
     */
    public static class ServerAckRetryEvent {
        private ServerAckPushData serverAckPush;
        private int retryCount;
        private int maxRetries;
        private String createTime;
        
        // Getters and Setters
        public ServerAckPushData getServerAckPush() { return serverAckPush; }
        public void setServerAckPush(ServerAckPushData serverAckPush) { this.serverAckPush = serverAckPush; }
        
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }
    
    /**
     * ServerAckPush的可序列化数据对象（避免Protobuf对象序列化问题）
     */
    public static class ServerAckPushData {
        private String toUserId;
        private String clientMsgId;
        private String msgId;
        private String chatId;
        private String ackTextDesc;
        private int msgReceivedStatus;
        private long receiveTime;
        
        // 从Protobuf对象转换（优化后：适配bytes/fixed64，chatId/ackTextDesc已删除）
        public static ServerAckPushData fromProtobuf(ServerAckPush push) {
            ServerAckPushData data = new ServerAckPushData();
            data.setToUserId(ProtoConverterUtil.longToSnowflakeString(push.getToUserId())); // fixed64 -> string
            data.setClientMsgId(ProtoConverterUtil.bytesToUuidString(push.getClientMsgId())); // bytes -> string
            data.setMsgId(ProtoConverterUtil.longToSnowflakeString(push.getMsgId())); // fixed64 -> string
            data.setChatId(""); // chatId已从proto删除，设为空串
            data.setAckTextDesc(""); // ackTextDesc已从proto删除，设为空串
            data.setMsgReceivedStatus(push.getMsgReceivedStatus());
            data.setReceiveTime(push.getReceiveTime());
            return data;
        }
        
        // 转换为Protobuf对象（优化后：适配bytes/fixed64，chatId/ackTextDesc已删除）
        public ServerAckPush toProtobuf() {
            return ServerAckPush.newBuilder()
                .setToUserId(toUserId != null && !toUserId.isEmpty() ? 
                    ProtoConverterUtil.snowflakeStringToLong(toUserId) : 0L) // string -> fixed64
                .setClientMsgId(clientMsgId != null && !clientMsgId.isEmpty() ? 
                    ProtoConverterUtil.uuidStringToBytes(clientMsgId) : com.google.protobuf.ByteString.EMPTY) // string -> bytes
                .setMsgId(msgId != null && !msgId.isEmpty() ? 
                    ProtoConverterUtil.snowflakeStringToLong(msgId) : 0L) // string -> fixed64
                // chatId已从proto删除
                // ackTextDesc已从proto删除
                .setMsgReceivedStatus(msgReceivedStatus)
                .setReceiveTime(receiveTime) // long -> fixed64（proto定义）
                .build();
        }
        
        // Getters and Setters
        public String getToUserId() { return toUserId; }
        public void setToUserId(String toUserId) { this.toUserId = toUserId; }
        
        public String getClientMsgId() { return clientMsgId; }
        public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
        
        public String getMsgId() { return msgId; }
        public void setMsgId(String msgId) { this.msgId = msgId; }
        
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
        
        public String getAckTextDesc() { return ackTextDesc; }
        public void setAckTextDesc(String ackTextDesc) { this.ackTextDesc = ackTextDesc; }
        
        public int getMsgReceivedStatus() { return msgReceivedStatus; }
        public void setMsgReceivedStatus(int msgReceivedStatus) { this.msgReceivedStatus = msgReceivedStatus; }
        
        public long getReceiveTime() { return receiveTime; }
        public void setReceiveTime(long receiveTime) { this.receiveTime = receiveTime; }
    }
}
