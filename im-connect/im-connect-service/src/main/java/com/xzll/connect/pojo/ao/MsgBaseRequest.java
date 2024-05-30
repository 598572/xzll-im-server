//package com.xzll.connect.pojo.ao;
//
//
//
//import lombok.*;
//
//import java.util.Map;
//import java.util.Objects;
//
///**
// * @Author: hzz
// * @Date: 2022/2/17 16:02:52
// * @Description: IM消息请求基类
// */
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//@ToString
//public class MsgBaseRequest<T> {
//
//
//    //消息类型
//    private MsgType msgType;
//    //消息业务属性和内容
//    private T body;
//    //附加属性 用于扩展
//    private Map<String, String> extraMap;
//
//
//    @Getter
//    @Setter
//    public static class MsgType {
//        private int firstLevelMsgType;
//        private int secondLevelMsgType;
//    }
//
//    public static boolean checkSupport(MsgType msgType, int firstLevelMsgTypeCode, int secondLevelMsgTypeCode) {
//        return Objects.nonNull(msgType) &&
//                msgType.getFirstLevelMsgType() == firstLevelMsgTypeCode && msgType.getSecondLevelMsgType() == secondLevelMsgTypeCode;
//    }
//
//}
