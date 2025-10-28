package com.xzll.connect.strategy.impl.c2c;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.util.ChatIdUtils;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.strategy.ProtoMsgHandlerStrategy;
import com.xzll.grpc.C2CWithdrawReq;
import com.xzll.grpc.ImProtoRequest;
import com.xzll.grpc.MsgType;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Description: 撤回消息 Protobuf 处理器
 */
@Slf4j
@Service
public class WithdrawMsgSendProtoStrategyImpl implements ProtoMsgHandlerStrategy {

    private static final String TAG = "[Protobuf客户端发送撤回消息]_";

    @Resource
    private C2CMsgProvider c2CMsgProvider;

    @Override
    public MsgType supportMsgType() {
        return MsgType.C2C_WITHDRAW;
    }

    @Override
    public void exchange(ChannelHandlerContext ctx, ImProtoRequest protoRequest) {
        log.info("{}exchange_method_start.", TAG);
        
        try {
            // 打印 ImProtoRequest 详细信息
            log.info("{}收到客户端消息 - 消息类型: {}, Payload大小: {} bytes", 
                TAG, protoRequest.getType(), protoRequest.getPayload().size());
            
            // 解析 C2CWithdrawReq
            C2CWithdrawReq req = C2CWithdrawReq.parseFrom(protoRequest.getPayload());
            
            // 打印消息详细内容
            log.info("{}消息详情 - msgId: {}, from: {}, to: {}, chatId: {}", 
                TAG, req.getMsgId(), req.getFrom(), req.getTo(), req.getChatId());
            
            // 参数校验
            if (StringUtils.isBlank(req.getMsgId()) || StringUtils.isBlank(req.getTo())) {
                log.warn("{}缺少必填参数", TAG);
                return;
            }
            
            // 转换为内部 AO 对象
            C2CWithdrawMsgAO packet = convertToAO(req);
            
            // 修改数据库中消息的撤回状态，并push消息至sender
            c2CMsgProvider.sendWithdrawMsg(packet);
            
            log.debug("{}结束", TAG);
        } catch (InvalidProtocolBufferException e) {
            log.error("{}解析 protobuf 消息失败", TAG, e);
        }
    }
    
    /**
     * 将 C2CWithdrawReq 转换为 C2CWithdrawMsgAO
     */
    private C2CWithdrawMsgAO convertToAO(C2CWithdrawReq req) {
        C2CWithdrawMsgAO ao = new C2CWithdrawMsgAO();
        ao.setMsgId(req.getMsgId());
        ao.setFromUserId(req.getFrom());
        ao.setToUserId(req.getTo());
        ao.setWithdrawFlag(1); // 1表示已撤回
        ao.setChatId(StringUtils.isNotBlank(req.getChatId()) ? req.getChatId() : 
            ChatIdUtils.buildC2CChatId(ImConstant.DEFAULT_BIZ_TYPE, Long.valueOf(req.getFrom()), Long.valueOf(req.getTo())));
        return ao;
    }
}

