package com.xzll.connect.strategy.impl.c2c;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.util.ChatIdUtils;
import com.xzll.common.util.ProtoConverterUtil;
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
            
            // 解析 C2CWithdrawReq（优化后：使用fixed64）
            C2CWithdrawReq req = C2CWithdrawReq.parseFrom(protoRequest.getPayload());
            
            // 打印消息详细内容（chatId已删除）
            log.info("{}消息详情 - msgId: {}, from: {}, to: {}", 
                TAG, req.getMsgId(), req.getFrom(), req.getTo());
            
            // 参数校验（fixed64不会为空，但需要检查是否>0）
            if (req.getMsgId() <= 0 || req.getTo() <= 0) {
                log.warn("{}缺少必填参数 - msgId: {}, to: {}", TAG, req.getMsgId(), req.getTo());
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
     * 将 C2CWithdrawReq 转换为 C2CWithdrawMsgAO（优化后：适配fixed64）
     */
    private C2CWithdrawMsgAO convertToAO(C2CWithdrawReq req) {
        C2CWithdrawMsgAO ao = new C2CWithdrawMsgAO();
        // fixed64 -> string
        ao.setMsgId(ProtoConverterUtil.longToSnowflakeString(req.getMsgId()));
        ao.setFromUserId(ProtoConverterUtil.longToSnowflakeString(req.getFrom()));
        ao.setToUserId(ProtoConverterUtil.longToSnowflakeString(req.getTo()));
        ao.setWithdrawFlag(1); // 1表示已撤回
        // chatId在proto中已删除，服务端根据from+to动态生成
        ao.setChatId(ChatIdUtils.buildC2CChatId(ImConstant.DEFAULT_BIZ_TYPE, req.getFrom(), req.getTo()));
        return ao;
    }
}

