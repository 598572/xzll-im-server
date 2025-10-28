package com.xzll.common.grpc;

import com.xzll.common.config.GrpcClientConfig;
import com.xzll.grpc.MessageServiceGrpc;
import lombok.extern.slf4j.Slf4j;

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
    public CompletableFuture<Boolean> pushFriendRequest(com.xzll.grpc.FriendRequestPush push) {
        return CompletableFuture.supplyAsync(() -> {
            totalRequests.incrementAndGet();
            try {
                SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStub(push.getToUserId());
                MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());
                boolean success = stub.pushFriendRequest2Client(push).getSuccess();
                if (success) {
                    successRequests.incrementAndGet();
                } else {
                    failureRequests.incrementAndGet();
                }
                return success;
            } catch (Exception e) {
                failureRequests.incrementAndGet();
                log.error("发送好友请求推送失败: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> pushFriendResponse(com.xzll.grpc.FriendResponsePush push) {
        return CompletableFuture.supplyAsync(() -> {
            totalRequests.incrementAndGet();
            try {
                SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStub(push.getToUserId());
                MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(stubWrapper.getChannelInfo().getChannel());
                boolean success = stub.pushFriendResponse2Client(push).getSuccess();
                if (success) {
                    successRequests.incrementAndGet();
                } else {
                    failureRequests.incrementAndGet();
                }
                return success;
            } catch (Exception e) {
                failureRequests.incrementAndGet();
                log.error("发送好友响应推送失败: {}", e.getMessage(), e);
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
