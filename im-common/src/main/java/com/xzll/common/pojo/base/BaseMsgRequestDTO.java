package com.xzll.common.pojo.base;


import com.xzll.common.pojo.MsgBaseRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 18:09:06
 * @Description: 发送消息-消息体基本属性
 */
@Getter
@Setter
public class BaseMsgRequestDTO {

    private String msgId;

    private MsgBaseRequest.MsgType msgType;

    private Long msgCreateTime;

    private String chatId;


}
