package com.xzll.connect.strategy.impl.c2c;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.util.ChatIdUtils;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.strategy.ProtoMsgHandlerStrategy;
import com.xzll.grpc.C2CAckReq;
import com.xzll.grpc.ImProtoRequest;
import com.xzll.grpc.MsgType;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * @Author: hzz
 * @Date: 2022/1/14 15:20:10
 * @Description: 客户端 ACK 消息 ，Protobuf 处理器
 */
@Slf4j
@Service
public class ClientReceivedMsgAckProtoStrategyImpl implements ProtoMsgHandlerStrategy {

    private static final String TAG = "[Protobuf客户端ACK消息]_";

    @Resource
    private C2CMsgProvider c2CMsgProvider;

    @Override
    public MsgType supportMsgType() {
        return MsgType.C2C_ACK;
    }

    @Override
    public void exchange(ChannelHandlerContext ctx, ImProtoRequest protoRequest) {
        log.debug("{}开始", TAG);
        
        try {
            // 打印 ImProtoRequest 详细信息
            log.info("{}收到客户端消息 - 消息类型: {}, Payload大小: {} bytes", 
                TAG, protoRequest.getType(), protoRequest.getPayload().size());
            
            // 解析 C2CAckReq
            C2CAckReq req = C2CAckReq.parseFrom(protoRequest.getPayload());
            
            // 打印消息详细内容（双轨制：显示两个ID）
            log.info("{}消息详情 - clientMsgId: {}, serverMsgId: {}, from: {}, to: {}, status: {}, chatId: {}", 
                TAG, req.getClientMsgId(), req.getMsgId(), req.getFrom(), req.getTo(), req.getStatus(), req.getChatId());
            
            // 转换为内部 AO 对象
            C2CReceivedMsgAckAO packet = convertToAO(req);
            
            //1. 修改数据库中消息的状态，并push消息至接收方，此处：修改db与发ack消息为同步。设计原则：要么第一步存消息就失败，要么：消息新增成功后，后边的状态流转一定要正确所以需要同步
            c2CMsgProvider.clientResponseAck(packet);
            
            log.debug("{}结束", TAG);
        } catch (InvalidProtocolBufferException e) {
            log.error("{}解析 protobuf 消息失败", TAG, e);
        }
    }
    
    /**
     * 将 C2CAckReq 转换为 C2CReceivedMsgAckAO（双轨制：保留 clientMsgId）
     */
    private C2CReceivedMsgAckAO convertToAO(C2CAckReq req) {
        C2CReceivedMsgAckAO ao = new C2CReceivedMsgAckAO();
        ao.setClientMsgId(req.getClientMsgId()); // ✅ 双轨制：保留客户端消息ID
        ao.setMsgId(req.getMsgId()); // 服务端消息ID
        ao.setFromUserId(req.getFrom());
        ao.setToUserId(req.getTo());
        ao.setMsgStatus(req.getStatus());
        ao.setMsgIds(Collections.singletonList(req.getMsgId()));
        ao.setChatId(req.getChatId().isEmpty() ? 
            ChatIdUtils.buildC2CChatId(null, Long.valueOf(req.getFrom()), Long.valueOf(req.getTo())) : 
            req.getChatId());
        return ao;
    }
}

