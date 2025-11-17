package com.xzll.business.handler.c2c;

import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.business.service.impl.ServerAckSimpleRetryService;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
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
    @Resource
    private ServerAckSimpleRetryService serverAckSimpleRetryService;


    /**
     * 离线消息处理 - 更新消息状态为离线并发送服务端ACK
     * 
     * 注意：离线消息本质上是一个更新消息状态的操作，消息已经在exchange方法中保存（初始状态为"未读"）
     * 这里只需要更新消息状态为"离线"，而不是重新保存消息
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2COffLineMsgAO dto) {
        log.debug("【C2COffLineMsgHandler开始】处理离线消息 - clientMsgId: {}, msgId: {}, from: {}, to: {}, status: {}",
            dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId(), dto.getMsgStatus());
        
        // 更新会话记录（如果需要）
        C2CSendMsgAO sendMsgAO = convertToC2CSendMsgAO(dto);
        boolean writeChat = imChatService.saveOrUpdateC2CChat(sendMsgAO);
        log.debug("【C2COffLineMsgHandler-会话保存】会话保存结果: {} - clientMsgId: {}, msgId: {}",
            writeChat, dto.getClientMsgId(), dto.getMsgId());
        
        // 更新消息状态为离线
        boolean updateMsg = true;
        if (imC2CMsgRecordHBaseService != null) {
            updateMsg = imC2CMsgRecordHBaseService.updateC2CMsgOffLineStatus(dto);
            log.debug("【C2COffLineMsgHandler-消息状态更新】HBase消息状态更新结果: {} - clientMsgId: {}, msgId: {}, status: {}",
                updateMsg, dto.getClientMsgId(), dto.getMsgId(), dto.getMsgStatus());
        } else {
            log.warn("【C2COffLineMsgHandler-跳过HBase】HBase服务未启用，跳过离线消息状态更新到HBase - clientMsgId: {}, msgId: {}", 
                dto.getClientMsgId(), dto.getMsgId());
        }
        
        if (writeChat && updateMsg) {
            log.debug("【C2COffLineMsgHandler-构建ACK】开始构建服务端ACK - clientMsgId: {}, msgId: {}, from: {}, to: {}",
                dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
            
            // 发送服务端ACK（优化后：string->bytes/fixed64，删除chatId/ackTextDesc）
            com.xzll.grpc.ServerAckPush ackPush = com.xzll.grpc.ServerAckPush.newBuilder()
                    .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(dto.getClientMsgId())) // string -> bytes
                    .setMsgId(ProtoConverterUtil.snowflakeStringToLong(dto.getMsgId())) // string -> fixed64
                    // chatId已从proto删除
                    .setToUserId(ProtoConverterUtil.snowflakeStringToLong(dto.getFromUserId())) // string -> fixed64
                    // ackTextDesc已从proto删除
                    .setMsgReceivedStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                    .setReceiveTime(System.currentTimeMillis()) // long -> fixed64（proto定义）
                    .build();
            
            log.debug("【C2COffLineMsgHandler-ACK构建完成】ServerAckPush构建完成 - clientMsgId: {}, msgId: {}, toUserId: {}, status: {}",
                ProtoConverterUtil.bytesToUuidString(ackPush.getClientMsgId()), ackPush.getMsgId(), ackPush.getToUserId(), ackPush.getMsgReceivedStatus());
                    
            // 使用MQ重试server ack消息
            CompletableFuture<Boolean> future = serverAckSimpleRetryService.sendServerAckWithRetry(ackPush);
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("【C2COffLineMsgHandler-ACK重试异常】离线消息服务端ACK(MQ重试)异常 - clientMsgId: {}, msgId: {}, error: {}", 
                        dto.getClientMsgId(), dto.getMsgId(), throwable.getMessage(), throwable);
                } else if (success) {
                    log.info("【C2COffLineMsgHandler-ACK发送成功】离线消息服务端ACK发送成功 - clientMsgId: {}, msgId: {}, from: {}, to: {}",
                        dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
                } else {
                    log.error("【C2COffLineMsgHandler-ACK最终失败】离线消息服务端ACK(MQ重试)最终失败 - clientMsgId: {}, msgId: {}, from: {}, to: {}", 
                        dto.getClientMsgId(), dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
                }
            });
        } else {
            log.error("【C2COffLineMsgHandler-更新失败】消息或会话更新失败 - clientMsgId: {}, msgId: {}, writeChat: {}, updateMsg: {}", 
                dto.getClientMsgId(), dto.getMsgId(), writeChat, updateMsg);
        }
        
        log.info("【C2COffLineMsgHandler完成】离线消息处理完成 - clientMsgId: {}, msgId: {}", 
            dto.getClientMsgId(), dto.getMsgId());
    }
    
    /**
     * 将C2COffLineMsgAO转换为C2CSendMsgAO（用于会话保存）
     */
    private C2CSendMsgAO convertToC2CSendMsgAO(C2COffLineMsgAO offLineMsgAO) {
        C2CSendMsgAO sendMsgAO = new C2CSendMsgAO();
        sendMsgAO.setClientMsgId(offLineMsgAO.getClientMsgId());
        sendMsgAO.setMsgId(offLineMsgAO.getMsgId());
        sendMsgAO.setFromUserId(offLineMsgAO.getFromUserId());
        sendMsgAO.setToUserId(offLineMsgAO.getToUserId());
        sendMsgAO.setMsgFormat(offLineMsgAO.getMsgFormat());
        sendMsgAO.setMsgContent(offLineMsgAO.getMsgContent());
        sendMsgAO.setMsgCreateTime(offLineMsgAO.getMsgCreateTime());
        sendMsgAO.setChatId(offLineMsgAO.getChatId());
        return sendMsgAO;
    }

}
