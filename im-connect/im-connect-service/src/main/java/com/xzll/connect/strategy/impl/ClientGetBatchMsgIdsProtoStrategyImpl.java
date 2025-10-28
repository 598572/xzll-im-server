package com.xzll.connect.strategy.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.util.msgId.SnowflakeIdService;
import com.xzll.connect.strategy.ProtoMsgHandlerStrategy;
import com.xzll.grpc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/16 15:20:10
 * @Description: id发号器， （一次生成多少可配在配置中心）, 因为有重试以及消息到达的顺序问题同时为了统一管理等诸多原因。所以id生成是基于长连接请求服务端，服务端来生成msgId
 */
@Slf4j
@Service
public class ClientGetBatchMsgIdsProtoStrategyImpl implements ProtoMsgHandlerStrategy {

    private static final String TAG = "[Protobuf客户端批量获取消息id]_";

    @Resource
    private SnowflakeIdService snowflakeIdService;

    @Override
    public MsgType supportMsgType() {
        return MsgType.GET_BATCH_MSG_IDS;
    }

    @Override
    public void exchange(ChannelHandlerContext ctx, ImProtoRequest protoRequest) {
        log.debug("{}开始", TAG);
        
        try {
            // 打印 ImProtoRequest 详细信息
            log.info("{}收到客户端消息 - 消息类型: {}, Payload大小: {} bytes", 
                TAG, protoRequest.getType(), protoRequest.getPayload().size());
            
            // 解析 GetBatchMsgIdsReq
            GetBatchMsgIdsReq req = GetBatchMsgIdsReq.parseFrom(protoRequest.getPayload());
            
            // 打印消息详细内容
            log.info("{}消息详情 - userId: {}", TAG, req.getUserId());
            
            // 生成一批消息id
            List<String> msgIds = snowflakeIdService.generateBatchMessageId(
                Long.parseLong(req.getUserId()), false);
            
            // 构建响应
            com.xzll.grpc.BatchMsgIdsPush resp = com.xzll.grpc.BatchMsgIdsPush.newBuilder()
                .addAllMsgIds(msgIds)
                .build();
            
            // 发送响应
            sendProtoResponse(ctx, MsgType.PUSH_BATCH_MSG_IDS, resp.toByteArray());
            
            log.debug("{}结束，返回{}个消息ID", TAG, msgIds.size());
        } catch (InvalidProtocolBufferException e) {
            log.error("{}解析 protobuf 消息失败", TAG, e);
        } catch (NumberFormatException e) {
            log.error("{}用户ID格式错误", TAG, e);
        }
    }
    
    /**
     * 发送 protobuf 响应消息
     */
    private void sendProtoResponse(ChannelHandlerContext ctx, MsgType msgType, byte[] payload) {
        try {
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(msgType)
                .setPayload(com.google.protobuf.ByteString.copyFrom(payload))
                .setCode(0)
                .build();
            
            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            ctx.channel().writeAndFlush(new BinaryWebSocketFrame(buf));
            log.debug("{}发送 protobuf 响应成功", TAG);
        } catch (Exception e) {
            log.error("{}发送 protobuf 响应失败", TAG, e);
        }
    }
}

