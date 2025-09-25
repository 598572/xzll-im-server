package com.xzll.common.grpc;

import com.xzll.common.config.GrpcClientConfig;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CWithdrawMsgVO;
import com.xzll.common.pojo.response.base.CommonMsgVO;
import com.xzll.grpc.MessageServiceGrpc;
import com.xzll.grpc.CommonMsgRequest;
import com.xzll.grpc.BatchSendRequest;
import com.xzll.grpc.BatchSendResponse;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 优雅的gRPC消息服务实现
 */
@Slf4j
public class ElegantGrpcMessageServiceImpl implements GrpcMessageService {

    @Resource
    private SmartGrpcClientManager grpcClientManager;
    
    @Resource
    private GrpcClientConfig grpcClientConfig;

    @Override
    public CompletableFuture<Boolean> sendServerAck(C2CServerReceivedMsgAckVO ackVo) {
        return CompletableFuture.supplyAsync(() -> sendViaGrpc(ackVo, "SERVER_ACK"));
    }

    @Override
    public CompletableFuture<Boolean> sendClientAck(C2CClientReceivedMsgAckVO ackVo) {
        return CompletableFuture.supplyAsync(() -> sendViaGrpc(ackVo, "CLIENT_ACK"));
    }

    @Override
    public CompletableFuture<Boolean> sendWithdrawMsg(C2CWithdrawMsgVO withdrawMsgVo) {
        return CompletableFuture.supplyAsync(() -> sendViaGrpc(withdrawMsgVo, "WITHDRAW"));
    }

    @Override
    public CompletableFuture<BatchSendResult> batchSendToUsers(List<String> userIds, CommonMsgVO message, String messageType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, List<String>> serverGroups = groupUsersByServer(userIds);
                List<CompletableFuture<ServerSendResult>> futures = serverGroups.entrySet().stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> 
                        sendToServer(entry.getKey(), entry.getValue(), message, messageType)))
                    .collect(Collectors.toList());

                return aggregateResults(futures);
            } catch (Exception e) {
                log.error("批量发送消息失败: {}", e.getMessage(), e);
                List<UserResult> failedResults = userIds.stream()
                    .map(uid -> new UserResult(uid, false, "批量发送失败", e.getMessage()))
                    .collect(Collectors.toList());
                return new BatchSendResult(userIds.size(), 0, userIds.size(), failedResults);
            }
        });
    }

    @Override
    public boolean sendToUserSync(String userId, CommonMsgVO message, String messageType) {
        try {
            return sendViaGrpc(message, messageType);
        } catch (Exception e) {
            log.error("同步发送消息失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Boolean> sendToUserAsync(String userId, CommonMsgVO message, String messageType) {
        return CompletableFuture.supplyAsync(() -> sendToUserSync(userId, message, messageType));
    }

    @Override
    public ServiceStats getServiceStats() {
        Map<String, Object> stats = grpcClientManager.getStats();
        return new ServiceStats(
            (Long) stats.getOrDefault("totalRequests", 0L),
            (Long) stats.getOrDefault("successRequests", 0L),
            (Long) stats.getOrDefault("failureRequests", 0L),
            (Integer) stats.getOrDefault("totalChannels", 0),
            (Double) stats.getOrDefault("successRate", 0.0),
            0L
        );
    }

    private boolean sendViaGrpc(CommonMsgVO message, String messageType) {
        try {
            // 从消息中获取目标用户ID
            String userId = getTargetUserId(message);
            if (userId == null) {
                log.error("无法获取目标用户ID，消息类型: {}", messageType);
                return false;
            }

            SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStub(userId);
            MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());

            boolean success;
            CommonMsgRequest req = buildCommonMsgRequest(message);
            switch (messageType) {
                case "SERVER_ACK":
                    success = stub.responseServerAck2Client(req).getSuccess();
                    break;
                case "CLIENT_ACK":
                    success = stub.responseClientAck2Client(req).getSuccess();
                    break;
                case "WITHDRAW":
                    success = stub.sendWithdrawMsg2Client(req).getSuccess();
                    break;
                default:
                    log.warn("未识别的消息类型: {}，默认按SERVER_ACK处理", messageType);
                    success = stub.responseServerAck2Client(req).getSuccess();
            }

            grpcClientManager.recordRequest(success);
            return success;
        } catch (Exception e) {
            log.error("gRPC发送消息失败: {}", e.getMessage(), e);
            grpcClientManager.recordRequest(false);
            return false;
        }
    }

    private String getTargetUserId(CommonMsgVO message) {
        if (message instanceof C2CServerReceivedMsgAckVO) {
            return ((C2CServerReceivedMsgAckVO) message).getToUserId();
        } else if (message instanceof C2CClientReceivedMsgAckVO) {
            return ((C2CClientReceivedMsgAckVO) message).getToUserId();
        } else if (message instanceof C2CWithdrawMsgVO) {
            return ((C2CWithdrawMsgVO) message).getToUserId();
        }
        return null;
    }

    private CommonMsgRequest buildCommonMsgRequest(CommonMsgVO message) {
        CommonMsgRequest.Builder b = CommonMsgRequest.newBuilder()
            .setUrl(message.getUrl() == null ? "" : message.getUrl())
            .setMsgId(message.getMsgId() == null ? "" : message.getMsgId())
            .setMsgCreateTime(message.getMsgCreateTime() == null ? System.currentTimeMillis() : message.getMsgCreateTime());

        if (message instanceof C2CServerReceivedMsgAckVO) {
            C2CServerReceivedMsgAckVO m = (C2CServerReceivedMsgAckVO) message;
            if (m.getToUserId() != null) b.setToUserId(m.getToUserId());
            if (m.getChatId() != null) b.setChatId(m.getChatId());
            if (m.getAckTextDesc() != null) b.setAckTextDesc(m.getAckTextDesc());
            if (m.getMsgReceivedStatus() != null) b.setMsgReceivedStatus(m.getMsgReceivedStatus());
            if (m.getReceiveTime() != null) b.setReceiveTime(m.getReceiveTime());
        } else if (message instanceof C2CClientReceivedMsgAckVO) {
            C2CClientReceivedMsgAckVO m = (C2CClientReceivedMsgAckVO) message;
            if (m.getToUserId() != null) b.setToUserId(m.getToUserId());
            if (m.getChatId() != null) b.setChatId(m.getChatId());
            if (m.getAckTextDesc() != null) b.setAckTextDesc(m.getAckTextDesc());
            if (m.getMsgReceivedStatus() != null) b.setMsgReceivedStatus(m.getMsgReceivedStatus());
            if (m.getReceiveTime() != null) b.setReceiveTime(m.getReceiveTime());
        } else if (message instanceof C2CWithdrawMsgVO) {
            C2CWithdrawMsgVO m = (C2CWithdrawMsgVO) message;
            if (m.getFromUserId() != null) b.setFromUserId(m.getFromUserId());
            if (m.getToUserId() != null) b.setToUserId(m.getToUserId());
            if (m.getWithdrawFlag() != null) b.setMsgStatus(m.getWithdrawFlag());
        }
        return b.build();
    }

    private Map<String, List<String>> groupUsersByServer(List<String> userIds) {
        return userIds.stream().collect(Collectors.groupingBy(userId -> {
            try {
                SmartGrpcClientManager.GrpcStubWrapper stub = grpcClientManager.getStub(userId);
                return stub.getChannelInfo().getIp() + ":" + grpcClientConfig.getDefaultPort();
            } catch (Exception e) {
                log.warn("获取用户 {} 服务器信息失败: {}", userId, e.getMessage());
                return "unknown";
            }
        }));
    }

    private ServerSendResult sendToServer(String serverKey, List<String> userIds, CommonMsgVO message, String messageType) {
        try {
            String[] parts = serverKey.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);
            SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStubByIP(ip, port);
            MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());

            BatchSendRequest req = BatchSendRequest.newBuilder()
                .addAllUserIds(userIds)
                .setMessage(buildCommonMsgRequest(message))
                .setMessageType(messageType)
                .build();

            BatchSendResponse resp = stub.batchSendToUsers(req);
            List<UserResult> results = resp.getResultsList().stream()
                .map(r -> new UserResult(r.getUserId(), r.getSuccess(), r.getMessage(), r.getError()))
                .collect(Collectors.toList());
            return new ServerSendResult(serverKey, results);
        } catch (Exception e) {
            log.error("向服务器 {} 发送消息失败: {}", serverKey, e.getMessage());
            List<UserResult> results = userIds.stream()
                .map(uid -> new UserResult(uid, false, "发送失败", e.getMessage()))
                .collect(Collectors.toList());
            return new ServerSendResult(serverKey, results);
        }
    }

    private BatchSendResult aggregateResults(List<CompletableFuture<ServerSendResult>> futures) {
        List<UserResult> allResults = futures.stream().map(CompletableFuture::join)
            .flatMap(result -> result.getResults().stream())
            .collect(Collectors.toList());
        int totalCount = allResults.size();
        int successCount = (int) allResults.stream().filter(UserResult::isSuccess).count();
        int failureCount = totalCount - successCount;
        return new BatchSendResult(totalCount, successCount, failureCount, allResults);
    }

    // 内部类定义
    public static class ServerSendResult {
        private final String serverKey;
        private final List<UserResult> results;

        public ServerSendResult(String serverKey, List<UserResult> results) {
            this.serverKey = serverKey;
            this.results = results;
        }

        // getters
        public String getServerKey() { return serverKey; }
        public List<UserResult> getResults() { return results; }
    }
} 