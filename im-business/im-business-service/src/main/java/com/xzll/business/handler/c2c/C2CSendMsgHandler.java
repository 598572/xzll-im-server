package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.business.service.UnreadCountService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import com.xzll.common.pojo.response.base.CommonMsgVO;
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
 * @Description: 发送单聊消息处理器 - 已升级为gRPC
 */
@Slf4j
@Component
public class C2CSendMsgHandler {
    @Resource
    private ImChatService imChatService;
    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordService;
    
    // 替换Dubbo为gRPC
    @Resource
    private GrpcMessageService grpcMessageService;
    
    @Resource
    private RedissonUtils redissonUtils;
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
        boolean writeMsg = imC2CMsgRecordService.saveC2CMsg(dto);
        if (writeChat && writeMsg) {
            // 增加接收方的未读消息数
            try {
                unreadCountService.incrementUnreadCount(dto.getToUserId(), dto.getChatId());
                log.info("增加未读消息数成功: toUserId={}, chatId={}", dto.getToUserId(), dto.getChatId());
            } catch (Exception e) {
                log.error("增加未读消息数失败: toUserId={}, chatId={}", dto.getToUserId(), dto.getChatId(), e);
                // 这里不抛异常，避免影响消息发送的主流程
            }
            
            //发送server_ack消息 告诉发送方此消息服务端已收到（想要可靠，必须落库后在ack）
            C2CServerReceivedMsgAckVO ackVo = getServerReceivedMsgAckVO(dto);
            
            // 使用gRPC发送ACK - 优雅的异步方式
            CompletableFuture<Boolean> future = grpcMessageService.sendServerAck(ackVo);
            
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

    /**
     * 构建响应给客户端的服务端ack
     *
     * @param packet
     * @return
     */
    public static C2CServerReceivedMsgAckVO getServerReceivedMsgAckVO(C2CSendMsgAO packet) {
        C2CServerReceivedMsgAckVO c2CServerReceivedMsgAckVO = new C2CServerReceivedMsgAckVO();
        c2CServerReceivedMsgAckVO.setAckTextDesc(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getDesc())
                .setMsgReceivedStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                //toUser是目标客户端也就是 发送方：fromUserId
                .setToUserId(packet.getFromUserId())
                .setUrl(ImSourceUrlConstant.C2C.SERVER_RECEIVE_ACK);
        c2CServerReceivedMsgAckVO.setMsgId(packet.getMsgId());
        return c2CServerReceivedMsgAckVO;
    }
}
