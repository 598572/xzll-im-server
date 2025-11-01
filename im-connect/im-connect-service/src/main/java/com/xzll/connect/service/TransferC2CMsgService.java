package com.xzll.connect.service;


import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.grpc.ImProtoRequest;

/**
 * @Author: hzz
 * @Date: 2024/5/30 15:56:57
 * @Description: 跨服务器消息转发服务（优化为直接传递 Protobuf 消息）
 */
public interface TransferC2CMsgService {

    /**
     * 跨服务器转发 Protobuf 消息
     * 
     * @param protoRequest Protobuf 消息请求（最小化体积，最快传输）
     * @return 处理结果
     */
    WebBaseResponse transferC2CMsg(ImProtoRequest protoRequest);
}
