package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.business.service.UnreadCountService;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.grpc.GrpcMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/6/16 20:23:33
 * @Description: 发送单聊消息处理器 - 已升级为gRPC
 */
@Slf4j
@Component
public class C2CSendMsgHandler {
    @Resource
    private ImChatService imChatService;
    
    @Autowired(required = false)
    private ImC2CMsgRecordHBaseService imC2CMsgRecordService;
    
    // 替换Dubbo为gRPC
    @Resource
    private GrpcMessageService grpcMessageService;
    @Resource
    private UnreadCountService unreadCountService;

    /**
     * 单聊消息 - 使用gRPC发送
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2CSendMsgAO dto) {
        boolean writeChat = imChatService.saveOrUpdateC2CChat(dto);
        boolean writeMsg = true;
        if (imC2CMsgRecordService != null) {
            writeMsg = imC2CMsgRecordService.saveC2CMsg(dto);
        } else {
            log.warn("HBase服务未启用，跳过消息存储到HBase，注意此举仅适用于开发环境");
        }
        if (writeChat && writeMsg) {
            // 增加接收方的未读消息数
            try {
                unreadCountService.incrementUnreadCount(dto.getToUserId(), dto.getChatId());
                log.info("增加未读消息数成功: toUserId={}, chatId={}", dto.getToUserId(), dto.getChatId());
            } catch (Exception e) {
                log.error("增加未读消息数失败: toUserId={}, chatId={}", dto.getToUserId(), dto.getChatId(), e);
                // 这里不抛异常，避免影响消息发送的主流程
            }
            
            // 发送server_ack（双轨制：包含客户端消息ID和服务端消息ID）
            com.xzll.grpc.ServerAckPush ackPush = com.xzll.grpc.ServerAckPush.newBuilder()
                    .setClientMsgId(dto.getClientMsgId()) // 客户端消息ID（用于客户端匹配）
                    .setMsgId(dto.getMsgId()) // 服务端消息ID
                    .setChatId(dto.getChatId())
                    //此时toUser是消息发送方 所以这里是fromUserId
                    .setToUserId(dto.getFromUserId())
                    .setAckTextDesc("SERVER_RECEIVED")
                    .setMsgReceivedStatus(com.xzll.common.constant.MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                    .setReceiveTime(System.currentTimeMillis())
                    .build();
            // 使用gRPC发送ACK - 异步方式
            CompletableFuture<Boolean> future = grpcMessageService.sendServerAck(ackPush);
            
            // 异步处理结果
            future.thenAccept(success -> {
                if (success) {
                    log.info("服务端ACK发送成功，用户: {}", dto.getFromUserId());
                } else {
                    log.error("服务端ACK发送失败，用户: {}", dto.getFromUserId());
                    // 可以在这里实现重试逻辑
                }
            }).exceptionally(throwable -> {
                log.error("服务端ACK发送异常，用户: {}, 异常: {}", dto.getFromUserId(), throwable.getMessage());
                return null;
            });
            
            log.info("gRPC服务端ACK发送任务已提交，用户: {}", dto.getFromUserId());
        }
    }

}
