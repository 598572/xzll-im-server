package com.xzll.business.handler.c2c;

import com.xzll.business.service.ChatListService;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.UnreadCountService;

import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.common.grpc.GrpcMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/6/16 20:23:33
 * @Description: 客户端接收消息后ack处理器 - 已升级为gRPC
 */
@Slf4j
@Component
public class C2CClientReceivedAckMsgHandler {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ImC2CMsgRecordHBaseService imC2CMsgRecordService;
    @Resource
    private GrpcMessageService grpcMessageService;
    @Resource
    private ChatListService chatListService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void clientReceivedAckMsgDeal(C2CReceivedMsgAckAO dto) {
        boolean updateResult = true;
        if (imC2CMsgRecordService != null) {
            updateResult = imC2CMsgRecordService.updateC2CMsgReceivedStatus(dto);
        } else {
            log.warn("HBase服务未启用，跳过更新消息状态，注意此举仅适用于开发环境");
        }

        //2. 如果是已读消息，清零Redis会话列表的未读数
        if (updateResult && MsgStatusEnum.MsgStatus.READED.getCode() == dto.getMsgStatus()) {
            try {
                // 清零Redis会话列表的未读数
                chatListService.clearUnreadCount(dto.getFromUserId(), dto.getChatId());
                log.info("清零会话列表未读数成功: userId={}, chatId={}", dto.getFromUserId(), dto.getChatId());
            } catch (Exception e) {
                log.error("清零会话列表未读数失败: userId={}, chatId={}", dto.getFromUserId(), dto.getChatId(), e);
                // 这里不抛异常，避免影响消息ACK的主流程
            }
        }

        //3. 【已废弃】（收到未读/已读ack后）删除离线消息缓存
        // 注：已不再使用Redis ZSet保存离线消息，客户端改为主动拉取会话列表
        // redissonUtils.removeZSetByScore(ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + dto.getFromUserId(), needDeleteMsgId, needDeleteMsgId);

        //4. 接收方客户端ack发送至发送方（优化后：string->bytes/fixed64，删除chatId/ackTextDesc）
        if (updateResult) {
            com.xzll.grpc.ClientAckPush ackPush = com.xzll.grpc.ClientAckPush.newBuilder()
                    .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(dto.getClientMsgId())) // string -> bytes
                    .setMsgId(ProtoConverterUtil.snowflakeStringToLong(dto.getMsgId())) // string -> fixed64
                    // chatId已从proto删除
                    .setToUserId(ProtoConverterUtil.snowflakeStringToLong(dto.getToUserId())) // string -> fixed64
                    // ackTextDesc已从proto删除
                    .setMsgReceivedStatus(dto.getMsgStatus())
                    .setReceiveTime(System.currentTimeMillis()) // long -> fixed64（proto定义）
                    .build();
            // 使用gRPC发送客户端ACK - 异步方式
            CompletableFuture<Boolean> future = grpcMessageService.sendClientAck(ackPush);
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("gRPC发送客户端ACK失败: {}", throwable.getMessage(), throwable);
                } else {
                    log.info("发送客户端ACK结果: success={}", success);
                }
            });
        }
    }

}
