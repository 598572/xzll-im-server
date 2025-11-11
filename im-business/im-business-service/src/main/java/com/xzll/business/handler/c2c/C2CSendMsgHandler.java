package com.xzll.business.handler.c2c;

import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.business.service.UnreadCountService;
import com.xzll.business.service.impl.ServerAckSimpleRetryService;
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
    @Resource
    private ServerAckSimpleRetryService serverAckSimpleRetryService;

    /**
     * 单聊消息 - 使用gRPC发送
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2CSendMsgAO dto) {
        log.debug("【C2CSendMsgHandler开始】处理在线消息 - clientMsgId: {}, msgId: {}, from: {}, to: {}",
            dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
        
        boolean writeChat = imChatService.saveOrUpdateC2CChat(dto);
        log.debug("【C2CSendMsgHandler-会话保存】会话保存结果: {} - clientMsgId: {}, msgId: {}",
            writeChat, dto.getClientMsgId(), dto.getMsgId());
        
        boolean writeMsg = true;
        if (imC2CMsgRecordService != null) {
            writeMsg = imC2CMsgRecordService.saveC2CMsg(dto);
            log.debug("【C2CSendMsgHandler-消息保存】HBase消息保存结果: {} - clientMsgId: {}, msgId: {}",
                writeMsg, dto.getClientMsgId(), dto.getMsgId());
        } else {
            log.warn("【C2CSendMsgHandler-跳过HBase】HBase服务未启用，跳过消息存储到HBase - clientMsgId: {}, msgId: {}", 
                dto.getClientMsgId(), dto.getMsgId());
        }
        if (writeChat && writeMsg) {
            // 增加接收方的未读消息数
            try {
                unreadCountService.incrementUnreadCount(dto.getToUserId(), dto.getChatId());
                log.debug("【C2CSendMsgHandler-未读数增加】未读消息数增加成功 - toUserId: {}, chatId: {}, clientMsgId: {}, msgId: {}",
                    dto.getToUserId(), dto.getChatId(), dto.getClientMsgId(), dto.getMsgId());
            } catch (Exception e) {
                log.error("【C2CSendMsgHandler-未读数失败】未读消息数增加失败 - toUserId: {}, chatId: {}, clientMsgId: {}, msgId: {}, error: {}", 
                    dto.getToUserId(), dto.getChatId(), dto.getClientMsgId(), dto.getMsgId(), e.getMessage(), e);
                // 这里不抛异常，避免影响消息发送的主流程
            }
            
            log.info("【C2CSendMsgHandler-构建ACK】开始构建服务端ACK - clientMsgId: {}, msgId: {}, from: {}, to: {}", 
                dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
            
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
            
            log.info("【C2CSendMsgHandler-ACK构建完成】ServerAckPush构建完成 - clientMsgId: {}, msgId: {}, toUserId: {}, status: {}", 
                ackPush.getClientMsgId(), ackPush.getMsgId(), ackPush.getToUserId(), ackPush.getMsgReceivedStatus());
                    
            // 使用MQ重试serve ack服务
            CompletableFuture<Boolean> future = serverAckSimpleRetryService.sendServerAckWithRetry(ackPush);
            
            // 异步处理结果
            future.thenAccept(success -> {
                if (success) {
                    log.info("【C2CSendMsgHandler-ACK发送成功】在线消息服务端ACK发送成功（MQ重试） - clientMsgId: {}, msgId: {}, from: {}", 
                        dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId());
                } else {
                    log.error("【C2CSendMsgHandler-ACK最终失败】在线消息服务端ACK(MQ重试)最终失败 - clientMsgId: {}, msgId: {}, from: {}", 
                        dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId());
                }
            }).exceptionally(throwable -> {
                log.error("【C2CSendMsgHandler-ACK重试异常】在线消息服务端ACK(MQ重试)异常 - clientMsgId: {}, msgId: {}, from: {}, error: {}", 
                    dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), throwable.getMessage(), throwable);
                return null;
            });
            
            log.info("【C2CSendMsgHandler-ACK提交】gRPC服务端ACK发送任务已提交 - clientMsgId: {}, msgId: {}, from: {}", 
                dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId());
        } else {
            log.error("【C2CSendMsgHandler-存储失败】消息或会话存储失败 - clientMsgId: {}, msgId: {}, writeChat: {}, writeMsg: {}", 
                dto.getClientMsgId(), dto.getMsgId(), writeChat, writeMsg);
        }
        
        log.info("【C2CSendMsgHandler完成】在线消息处理完成 - clientMsgId: {}, msgId: {}", 
            dto.getClientMsgId(), dto.getMsgId());
    }

}
