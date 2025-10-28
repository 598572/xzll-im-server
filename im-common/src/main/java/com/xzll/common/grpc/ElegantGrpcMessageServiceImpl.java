package com.xzll.common.grpc;

import com.xzll.common.config.GrpcClientConfig;
import com.xzll.grpc.MessageServiceGrpc;
import com.xzll.grpc.FriendRequestPush;
import lombok.extern.slf4j.Slf4j;
import com.xzll.common.pojo.response.FriendRequestPushVO;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC消息服务实现 - 类型化Push设计
 */
@Slf4j
public class ElegantGrpcMessageServiceImpl implements GrpcMessageService {

    @Resource
    private SmartGrpcClientManager grpcClientManager;
    
    @Resource
    private GrpcClientConfig grpcClientConfig;

    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successRequests = new AtomicLong(0);
    private final AtomicLong failureRequests = new AtomicLong(0);

    @Override
    public CompletableFuture<Boolean> sendServerAck(com.xzll.grpc.ServerAckPush push) {
        return CompletableFuture.supplyAsync(() -> {
            totalRequests.incrementAndGet();
            try {
                SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStub(push.getToUserId());
                MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());
                boolean success = stub.responseServerAck2Client(push).getSuccess();
                if (success) {
                    successRequests.incrementAndGet();
                } else {
                    failureRequests.incrementAndGet();
                }
                return success;
            } catch (Exception e) {
                failureRequests.incrementAndGet();
                log.error("发送服务端ACK失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> sendClientAck(com.xzll.grpc.ClientAckPush push) {
        return CompletableFuture.supplyAsync(() -> {
            totalRequests.incrementAndGet();
            try {
                SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStub(push.getToUserId());
                MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());
                boolean success = stub.responseClientAck2Client(push).getSuccess();
                if (success) {
                    successRequests.incrementAndGet();
                } else {
                    failureRequests.incrementAndGet();
                }
                return success;
            } catch (Exception e) {
                failureRequests.incrementAndGet();
                log.error("发送客户端ACK失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> sendWithdrawMsg(com.xzll.grpc.WithdrawPush push) {
        return CompletableFuture.supplyAsync(() -> {
            totalRequests.incrementAndGet();
            try {
                SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStub(push.getToUserId());
                MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());
                boolean success = stub.sendWithdrawMsg2Client(push).getSuccess();
                if (success) {
                    successRequests.incrementAndGet();
                } else {
                    failureRequests.incrementAndGet();
                }
                return success;
            } catch (Exception e) {
                failureRequests.incrementAndGet();
                log.error("发送撤回消息失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> sendToUserAsync(String userId, FriendRequestPushVO message, String messageType) {
        return CompletableFuture.supplyAsync(() -> {
            totalRequests.incrementAndGet();
            try {
                // 将 VO 转换为 Protobuf Push
                FriendRequestPush push = FriendRequestPush.newBuilder()
                        .setFromUserId(message.getFromUserId() != null ? message.getFromUserId() : "")
                        .setToUserId(message.getToUserId() != null ? message.getToUserId() : "")
                        .setRequestMessage(message.getPushContent() != null ? message.getPushContent() : "")
                        .setRequestTime(message.getMsgCreateTime() != null ? message.getMsgCreateTime() : System.currentTimeMillis())
                        .build();

                // TODO: 实现好友推送的 gRPC 调用（暂未实现，需要在 proto 中添加对应的 rpc 方法）
                log.warn("好友推送功能暂未实现 gRPC 接口，userId={}, messageType={}", userId, messageType);
                
                successRequests.incrementAndGet();
                return true;
            } catch (Exception e) {
                failureRequests.incrementAndGet();
                log.error("发送好友推送失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    @Override
    public ServiceStats getServiceStats() {
        long total = totalRequests.get();
        long success = successRequests.get();
        long failure = failureRequests.get();
        double successRate = total > 0 ? (double) success / total : 0.0;
        
        return new ServiceStats(
                total,
                success,
                failure,
                0,  // activeConnections 暂未实现
                successRate,
                0L  // 平均响应时间暂未实现
        );
    }
}
