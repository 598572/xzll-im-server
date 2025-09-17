package com.xzll.connect.rpcapi;


import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.response.base.CommonMsgVO;


/**
 * @Author: hzz
 * @Date: 2024/5/30 15:56:57
 * @Description: 客户端如果在一定时间没收到 需要消息重试！最大努力确保可靠到达
 */
public interface RpcSendMsg2ClientApi {

    /**
     * 服务端响应ack给消息发送 客户端
     *
     * @param packet
     * @return
     */
    public WebBaseResponse responseServerAck2Client(CommonMsgVO packet);

    /**
     * 接收方响应ack给消息发送 客户端
     *
     * @param packet
     * @return
     */
    public WebBaseResponse responseClientAck2Client(CommonMsgVO packet);

    /**
     * 发送撤回消息
     *
     * @param packet
     * @return
     */
    public WebBaseResponse sendWithdrawMsg2Client(CommonMsgVO packet);

    /**
     * 发送好友申请推送消息
     *
     * @param packet
     * @return
     */
    public WebBaseResponse sendFriendRequestPush2Client(CommonMsgVO packet);
}
