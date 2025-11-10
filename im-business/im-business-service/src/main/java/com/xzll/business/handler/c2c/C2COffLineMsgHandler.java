package com.xzll.business.handler.c2c;

import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CSendMsgAO;
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
 * @Description: 离线消息处理器 - 已升级为gRPC
 */
@Slf4j
@Component
public class C2COffLineMsgHandler {
    @Resource
    private ImChatService imChatService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;
    
    // 替换Dubbo为gRPC
    @Resource
    private GrpcMessageService grpcMessageService;


    /**
     * 离线消息处理 - 入库并发送服务端ACK
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2CSendMsgAO dto) {
        log.debug("【C2COffLineMsgHandler开始】处理离线消息 - clientMsgId: {}, msgId: {}, from: {}, to: {}",
            dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
        
        boolean writeChat = imChatService.saveOrUpdateC2CChat(dto);
        log.debug("【C2COffLineMsgHandler-会话保存】会话保存结果: {} - clientMsgId: {}, msgId: {}",
            writeChat, dto.getClientMsgId(), dto.getMsgId());
        
        boolean writeMsg = true;
        if (imC2CMsgRecordHBaseService != null) {
            writeMsg = imC2CMsgRecordHBaseService.saveC2CMsg(dto);
            log.debug("【C2COffLineMsgHandler-消息保存】HBase消息保存结果: {} - clientMsgId: {}, msgId: {}",
                writeMsg, dto.getClientMsgId(), dto.getMsgId());
        } else {
            log.warn("【C2COffLineMsgHandler-跳过HBase】HBase服务未启用，跳过离线消息存储到HBase - clientMsgId: {}, msgId: {}", 
                dto.getClientMsgId(), dto.getMsgId());
        }
        
        if (writeChat && writeMsg) {
            log.debug("【C2COffLineMsgHandler-构建ACK】开始构建服务端ACK - clientMsgId: {}, msgId: {}, from: {}, to: {}",
                dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
            
            // 发送服务端ACK，告知发送方消息已接收并存储（双轨制：包含客户端消息ID）
            com.xzll.grpc.ServerAckPush ackPush = com.xzll.grpc.ServerAckPush.newBuilder()
                    .setClientMsgId(dto.getClientMsgId()) // 客户端消息ID
                    .setMsgId(dto.getMsgId()) // 服务端消息ID
                    .setChatId(dto.getChatId())
                    .setToUserId(dto.getFromUserId())
                    .setAckTextDesc("SERVER_RECEIVED")
                    .setMsgReceivedStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                    .setReceiveTime(System.currentTimeMillis())
                    .build();
            
            log.debug("【C2COffLineMsgHandler-ACK构建完成】ServerAckPush构建完成 - clientMsgId: {}, msgId: {}, toUserId: {}, status: {}",
                ackPush.getClientMsgId(), ackPush.getMsgId(), ackPush.getToUserId(), ackPush.getMsgReceivedStatus());
                    
            // 使用gRPC发送服务端ACK  - 异步方式
            CompletableFuture<Boolean> future = grpcMessageService.sendServerAck(ackPush);
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("【C2COffLineMsgHandler-ACK发送失败】离线消息服务端ACK发送失败 - clientMsgId: {}, msgId: {}, error: {}", 
                        dto.getClientMsgId(), dto.getMsgId(), throwable.getMessage(), throwable);
                } else {
                    log.info("【C2COffLineMsgHandler-ACK发送成功】离线消息服务端ACK发送成功 - clientMsgId: {}, msgId: {}, from: {}, to: {}", 
                        dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
                }
            });
        } else {
            log.error("【C2COffLineMsgHandler-存储失败】消息或会话存储失败 - clientMsgId: {}, msgId: {}, writeChat: {}, writeMsg: {}", 
                dto.getClientMsgId(), dto.getMsgId(), writeChat, writeMsg);
        }
        
        log.info("【C2COffLineMsgHandler完成】离线消息处理完成 - clientMsgId: {}, msgId: {}", 
            dto.getClientMsgId(), dto.getMsgId());
    }

}
