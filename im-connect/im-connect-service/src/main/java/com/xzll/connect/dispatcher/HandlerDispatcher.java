package com.xzll.connect.dispatcher;



import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.grpc.ImProtoRequest;
import com.xzll.grpc.MsgType;
import com.xzll.connect.strategy.ProtoMsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * @Author: hzz
 * @Date: 2022/1/14 13:24:34
 * @Description: 消息分发器 - 纯 Protobuf 模式（职责分离原则）
 */
@Slf4j
@Component
public class HandlerDispatcher implements ApplicationContextAware {

    private final Map<MsgType, ProtoMsgHandlerStrategy> protoHandlers = new HashMap<>();

    /**
     * 分发客户端直连的 Protobuf 消息
     * 
     * 场景：WebSocket 客户端直接发送消息
     * 职责：保存消息 + 推送/转发
     *
     * @param ctx          ChannelHandlerContext
     * @param protoRequest Protobuf 请求
     */
    public void dispatcher(ChannelHandlerContext ctx, ImProtoRequest protoRequest) {
        MsgType msgType = protoRequest.getType();
        ProtoMsgHandlerStrategy handler = protoHandlers.get(msgType);
        if (handler == null) {
            log.warn("[客户端直连] 未找到 protobuf 消息处理器, msgType: {}", msgType);
            return;
        }
        try {
            handler.exchange(ctx, protoRequest);
        } catch (Exception e) {
            log.error("[客户端直连] protobuf 消息处理异常, msgType: {}", msgType, e);
        }
    }

    /**
     * 接收并转发跨服务器的 Protobuf 消息
     * 
     * 场景：gRPC 跨服务器调用，目标服务器接收并转发消息
     * 职责：不保存消息（因为转发前已发到mq 消费者已经报错了，避免重复）+ 二次校验 + 直接推送
     *
     * @param protoRequest Protobuf 消息请求
     * @return 处理结果
     */
    public WebBaseResponse receiveAndSendMsg(ImProtoRequest protoRequest) {
        if (CollectionUtils.isEmpty(protoHandlers)) {
            log.warn("[跨服务器转发] 无可用的 Protobuf 消息处理器");
            return WebBaseResponse.returnResultError("无处理器");
        }
        
        MsgType msgType = protoRequest.getType();
        ProtoMsgHandlerStrategy handler = protoHandlers.get(msgType);
        
        if (handler == null) {
            log.warn("[跨服务器转发] 未找到支持该消息类型的处理器, msgType: {}", msgType);
            return WebBaseResponse.returnResultError("无处理器");
        }
        
        try {
            // 调用处理器的跨服务器转发方法
            return handler.receiveAndSendMsg(protoRequest);
        } catch (Exception e) {
            log.error("[跨服务器转发] 处理 Protobuf 消息异常, msgType: {}", msgType, e);
            return WebBaseResponse.returnResultError("处理失败: " + e.getMessage());
        }
    }


    /**
     * 加载 Protobuf 策略实现
     *
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 加载 Protobuf 消息处理器（唯一支持的格式）
        Collection<ProtoMsgHandlerStrategy> protoStrategies = 
            applicationContext.getBeansOfType(ProtoMsgHandlerStrategy.class).values();
        for (ProtoMsgHandlerStrategy strategy : protoStrategies) {
            MsgType type = strategy.supportMsgType();
            protoHandlers.put(type, strategy);
            log.info("注册 Protobuf 消息处理器: {} -> {}", type, strategy.getClass().getSimpleName());
        }
        log.info("=== IM-Connect 已启用纯 Protobuf 模式，共注册 {} 个处理器 ===", protoHandlers.size());
    }
}
