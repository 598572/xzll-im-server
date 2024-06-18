package com.xzll.common.pojo.base;


import com.xzll.common.pojo.MsgBaseRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 18:09:06
 * @Description: 消息响应基类
 */
@Getter
@Setter
public class BaseMsgResponseDTO {

    private String msgId;
    private MsgBaseRequest.MsgType msgType;

}
