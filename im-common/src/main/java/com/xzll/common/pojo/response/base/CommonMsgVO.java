package com.xzll.common.pojo.response.base;


import com.xzll.common.pojo.base.ImBaseResponse;
import lombok.Setter;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2022/1/18 18:09:06
 * @Description: 服务端响应消息-消息体基本属性 其实就是ImBaseResponse的 body ：所有《服务端》向【客户端】发的body 都继承于此 且后缀均为  VO  ！
 */
@Setter
public class CommonMsgVO implements Serializable {
    private static final long serialVersionUID = -1L;

    private String msgId;
    private ImBaseResponse.MsgType msgType;
    private Long msgCreateTime;

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public ImBaseResponse.MsgType getMsgType() {
        return msgType;
    }

    public void setMsgType(ImBaseResponse.MsgType msgType) {
        this.msgType = msgType;
    }

    public void setMsgCreateTime(Long msgCreateTime) {
        this.msgCreateTime = msgCreateTime;
    }

    public Long getMsgCreateTime() {
        if (this.msgCreateTime == null) {
            this.msgCreateTime = System.currentTimeMillis();
            return this.msgCreateTime;
        }
        return msgCreateTime;
    }
}
