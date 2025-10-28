package com.xzll.business.handler.c2c;


import com.xzll.business.service.ImC2CMsgRecordHBaseService;


import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.grpc.GrpcMessageService;
import lombok.extern.slf4j.Slf4j;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/6/16 20:23:33
 * @Description: 客户端响应的ack消息 处理器
 */
@Slf4j
@Component
public class C2CClientWithdrawMsgHandler {

    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordService;
    @Resource
    private RedissonUtils redissonUtils;
    @Resource
    private GrpcMessageService grpcMessageService;


    /**
     * 接收方响应的 ack消息
     *
     * @param ao
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void clientWithdrawMsgDeal(C2CWithdrawMsgAO ao) {
        //1. 更新消息状态为：未读/已读
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgWithdrawStatus(ao);
        //2. 撤回消息发送至接收方
        if (updateResult) {
            com.xzll.grpc.WithdrawPush withdrawPush = com.xzll.grpc.WithdrawPush.newBuilder()
                    .setMsgId(ao.getMsgId())
                    .setChatId(ao.getChatId())
                    .setFromUserId(ao.getFromUserId())
                    .setToUserId(ao.getToUserId())
                    .build();
            // 使用gRPC发送撤回 - 异步方式
            CompletableFuture<Boolean> future = grpcMessageService.sendWithdrawMsg(withdrawPush);
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("gRPC发送撤回消息失败: {}", throwable.getMessage(), throwable);
                } else {
                    log.info("发送撤回消息至接收方结果: success={}", success);
                }
            });
        }
    }

}
