package com.xzll.connect.api;



import com.xzll.common.pojo.base.BaseMsgRequestDTO;

/**
 * @Author: hzz
 * @Date: 2024/5/30 15:56:57
 * @Description: 服务端响应ack给消息发送 客户端，客户端如果在一定时间没收到 需要消息重试！最大努力确保可靠到达
 */
public interface ServerResponseAck2ClientApi {

    void serverResponseAck2Client(BaseMsgRequestDTO packet);
}
