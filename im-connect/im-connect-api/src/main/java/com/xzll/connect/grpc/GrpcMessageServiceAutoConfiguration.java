package com.xzll.connect.grpc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC消息服务自动配置
 */
@Configuration
public class GrpcMessageServiceAutoConfiguration {
    
    /**
     * 当没有 GrpcMessageService 实现时，提供一个默认的空实现
     * 这样 im-business 启动时不会报错，但实际调用时需要在运行时提供真正的实现
     */
    @Bean
    @ConditionalOnMissingBean(GrpcMessageService.class)
    public GrpcMessageService defaultGrpcMessageService() {
        return new GrpcMessageService() {
            @Override
            public java.util.concurrent.CompletableFuture<Boolean> sendServerAck(com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO ackVo) {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }

            @Override
            public java.util.concurrent.CompletableFuture<Boolean> sendClientAck(com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO ackVo) {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }

            @Override
            public java.util.concurrent.CompletableFuture<Boolean> sendWithdrawMsg(com.xzll.common.pojo.response.C2CWithdrawMsgVO withdrawMsgVo) {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }

            @Override
            public java.util.concurrent.CompletableFuture<BatchSendResult> batchSendToUsers(java.util.List<String> userIds, com.xzll.common.pojo.response.base.CommonMsgVO message, String messageType) {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }

            @Override
            public boolean sendToUserSync(String userId, com.xzll.common.pojo.response.base.CommonMsgVO message, String messageType) {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }

            @Override
            public java.util.concurrent.CompletableFuture<Boolean> sendToUserAsync(String userId, com.xzll.common.pojo.response.base.CommonMsgVO message, String messageType) {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }

            @Override
            public ServiceStats getServiceStats() {
                throw new UnsupportedOperationException("GrpcMessageService 实现未找到，请确保 im-connect-service 已启动");
            }
        };
    }
}
