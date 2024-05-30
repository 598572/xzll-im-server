package com.xzll.common.pojo;

import lombok.*;

import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2022/2/17 16:02:52
 * @Description: IM消息类响应基类
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MsgBaseResponse<T> {

    //消息类型
    private MsgType msgType;
    //消息业务属性和内容
    private T body;
    //附加属性 用于扩展
    private Map<String, String> extraMap;


    @Getter
    @Setter
    public static class MsgType {
        private int firstLevelMsgType;
        private int secondLevelMsgType;
    }

    public static <T> MsgBaseResponse buildPushToClientData(MsgBaseRequest.MsgType msgType, T data, Map<String, String> extraMap) {
        MsgType msgTypeResponse = new MsgType();
        msgTypeResponse.setFirstLevelMsgType(msgType.getFirstLevelMsgType());
        msgTypeResponse.setSecondLevelMsgType(msgType.getSecondLevelMsgType());
        return new MsgBaseResponse(msgTypeResponse, data, extraMap);
    }

    public static <T> MsgBaseResponse buildPushToClientData(MsgBaseRequest.MsgType msgType, T data) {
        MsgType msgTypeResponse = new MsgType();
        msgTypeResponse.setFirstLevelMsgType(msgType.getFirstLevelMsgType());
        msgTypeResponse.setSecondLevelMsgType(msgType.getSecondLevelMsgType());
        return new MsgBaseResponse(msgTypeResponse, data, null);
    }
}
