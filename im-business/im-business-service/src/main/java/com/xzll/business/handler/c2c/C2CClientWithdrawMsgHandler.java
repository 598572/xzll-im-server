package com.xzll.business.handler.c2c;


import com.xzll.business.service.ImC2CMsgRecordService;


import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.common.grpc.GrpcMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/6/16 20:23:33
 * @Description: 客户端响应的ack消息 处理器
 */
@Slf4j
@Component
public class C2CClientWithdrawMsgHandler {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ImC2CMsgRecordService imC2CMsgRecordService;
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
        boolean updateResult = true;
        if (imC2CMsgRecordService != null) {
            updateResult = imC2CMsgRecordService.updateC2CMsgWithdrawStatus(ao);
        } else {
            log.warn("HBase服务未启用，跳过更新撤回状态，注意此举仅适用于开发环境");
        }
        //2. 撤回消息发送至接收方（优化后：string->fixed64，删除chatId）
        if (updateResult) {
            com.xzll.grpc.WithdrawPush withdrawPush = com.xzll.grpc.WithdrawPush.newBuilder()
                    .setMsgId(ProtoConverterUtil.snowflakeStringToLong(ao.getMsgId())) // string -> fixed64
                    // chatId已从proto删除
                    .setFromUserId(ProtoConverterUtil.snowflakeStringToLong(ao.getFromUserId())) // string -> fixed64
                    .setToUserId(ProtoConverterUtil.snowflakeStringToLong(ao.getToUserId())) // string -> fixed64
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
