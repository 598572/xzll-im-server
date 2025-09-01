package com.xzll.business.handler.c2c;

import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.grpc.SmartGrpcClientManager;
import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.common.util.NettyAttrUtil;
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

    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordService;
    @Resource
    private RedissonUtils redissonUtils;
    @Resource
    private SmartGrpcClientManager grpcClientManager;
    @Resource
    private GrpcMessageService grpcMessageService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void clientReceivedAckMsgDeal(C2CReceivedMsgAckAO dto) {
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgReceivedStatus(dto);
        long needDeleteMsgId = com.xzll.common.util.msgId.SnowflakeIdService.getSnowflakeId(dto.getMsgId());
        redissonUtils.removeZSetByScore(ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + dto.getFromUserId(), needDeleteMsgId, needDeleteMsgId);

        if (updateResult) {
            C2CClientReceivedMsgAckVO ackVo = getClientReceivedMsgAckVO(dto);
            // 使用GrpcMessageService统一发送客户端ACK
            CompletableFuture<Boolean> future = grpcMessageService.sendClientAck(ackVo);
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("gRPC发送客户端ACK失败: {}", throwable.getMessage(), throwable);
                } else {
                    log.info("发送客户端ACK结果: success={}", success);
                }
            });
        }
    }

    public static C2CClientReceivedMsgAckVO getClientReceivedMsgAckVO(C2CReceivedMsgAckAO packet) {
        C2CClientReceivedMsgAckVO clientReceivedMsgAckDTO = new C2CClientReceivedMsgAckVO();
        clientReceivedMsgAckDTO.setAckTextDesc(MsgStatusEnum.MsgStatus.getNameByCode(packet.getMsgStatus()))
                .setMsgReceivedStatus(packet.getMsgStatus())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                //toUser是目标客户端也就是 最初发消息的发送方，对于接收方响应ack时来说 发送方就变成：toUserId 了
                .setToUserId(packet.getToUserId())
                .setUrl(packet.getUrl());
        clientReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return clientReceivedMsgAckDTO;
    }

}
