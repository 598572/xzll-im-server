package com.xzll.connect.strategy;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.grpc.ImProtoRequest;
import com.xzll.grpc.MsgType;
import io.netty.channel.ChannelHandlerContext;

/**
 * @Author: hzz
 * @Description: Protobuf 消息处理策略接口（职责分离设计）
 */
public interface ProtoMsgHandlerStrategy {

    /**
     * 返回该策略支持的消息类型
     *
     * @return MsgType
     */
    MsgType supportMsgType();

    /**
     * 处理客户端直连的 Protobuf 消息（对应原有的 exchange 方法）
     * 
     * 职责：
     * 1. 保存消息到数据库
     * 2. 查找接收人并推送/转发
     *
     * @param ctx          ChannelHandlerContext（不为 null）
     * @param protoRequest Protobuf 请求
     */
    void exchange(ChannelHandlerContext ctx, ImProtoRequest protoRequest);

    /**
     * 接收并转发跨服务器的 Protobuf 消息（对应原有的 receiveAndSendMsg 方法）
     * 
     * 职责：
     * 1. 不保存消息（避免重复，消息已在源服务器保存）
     * 2. 二次校验接收人在线状态
     * 3. 直接推送给本地客户端
     *
     * @param protoRequest Protobuf 请求
     * @return 处理结果
     */
    default WebBaseResponse receiveAndSendMsg(ImProtoRequest protoRequest) {
        // 默认实现：只有需要跨服务器转发的消息类型才需要重写此方法
        return WebBaseResponse.returnResultError("该消息类型不支持跨服务器转发");
    }
}

