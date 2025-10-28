package com.xzll.connect.service.impl;


import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import com.xzll.connect.service.TransferC2CMsgService;
import com.xzll.grpc.ImProtoRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/5/30 16:04:34
 * @Description: 跨服务器消息转发服务（优化为直接传递 Protobuf 消息）
 */
@Service
@Slf4j
public class TransferC2CMsgServiceImpl implements TransferC2CMsgService {

    @Resource
    private HandlerDispatcher handlerDispatcher;

    /**
     * 跨服务器转发 Protobuf 消息（优化版：直接传递 ImProtoRequest）
     * 
     * 优势：
     * 1. 体积最小：无额外包装，只传核心消息
     * 2. 传输最快：减少序列化/反序列化开销
     * 3. 带宽最省：无冗余字段（URL、headers 等）
     * 4. 类型明确：MsgType 枚举，无需 URL 路由
     * 
     * @param protoRequest Protobuf 消息请求
     * @return 处理结果
     */
    @Override
    public WebBaseResponse transferC2CMsg(ImProtoRequest protoRequest) {
        try {
            if (protoRequest == null) {
                log.warn("[跨服务器转发] 请求参数为空");
                return WebBaseResponse.returnResultError("请求参数为空");
            }
            
            log.info("[跨服务器转发] 接收消息, msgType: {}", protoRequest.getType());
            
            // 调用分发器处理消息
            return handlerDispatcher.receiveAndSendMsg(protoRequest);
            
        } catch (Exception e) {
            log.error("[跨服务器转发] 处理消息失败", e);
            return WebBaseResponse.returnResultError("处理失败: " + e.getMessage());
        }
    }
}
