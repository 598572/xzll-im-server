package com.xzll.business.handler.c2c;

import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CSendMsgAO;
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
 * @Description: 离线消息处理器 - 已升级为gRPC
 */
@Slf4j
@Component
public class C2COffLineMsgHandler {
    @Resource
    private ImChatService imChatService;
    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;
    
    // 替换Dubbo为gRPC
    @Resource
    private GrpcMessageService grpcMessageService;
    
    @Resource
    private RedissonUtils redissonUtils;

    /**
     * 离线消息处理 - 入库并发送服务端ACK
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2CSendMsgAO dto) {
        boolean writeChat = imChatService.saveOrUpdateC2CChat(dto);
        boolean writeMsg = imC2CMsgRecordHBaseService.saveC2CMsg(dto);
        if (writeChat && writeMsg) {
            // 发送服务端ACK，告知发送方消息已接收并存储
            com.xzll.grpc.ServerAckPush ackPush = com.xzll.grpc.ServerAckPush.newBuilder()
                    .setMsgId(dto.getMsgId())
                    .setChatId(dto.getChatId())
                    .setToUserId(dto.getFromUserId())
                    .setAckTextDesc("SERVER_RECEIVED")
                    .setMsgReceivedStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                    .setReceiveTime(System.currentTimeMillis())
                    .build();
            // 使用gRPC发送服务端ACK  - 异步方式
            CompletableFuture<Boolean> future = grpcMessageService.sendServerAck(ackPush);
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("离线消息服务端ACK发送失败: {}", throwable.getMessage(), throwable);
                } else {
                    log.info("离线消息服务端ACK发送成功，msgId:{} from:{} to:{}", dto.getMsgId(), dto.getFromUserId(), dto.getToUserId());
                }
            });
        }
    }

}
