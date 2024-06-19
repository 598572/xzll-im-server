package com.xzll.common.constant;

import lombok.Getter;

/**
 * TODO !!!!!!    暂定根据消息类型来路由处理消息， 后期可能改造成 url 更直接方便 ！
 */
public interface MsgTypeEnum {


    @Getter
    public enum FirstLevelMsgType {

        /**
         * 消息类型
         * 1、指令消息（撤回）
         * 2、通知消息（服务端接收消息后的ack、已读）
         * 3、聊天消息（单聊、重新发送）
         * 4、系统消息（由系统发送的消息）
         * 5、获取数据的消息（比如获取一批消息id，获取其他等等信息 都可以使用此消息类型）
         */

        COMMAND_MSG(1, "指令消息"),
        ACK_MSG(2, "ack消息"),
        CHAT_MSG(3, "聊天消息"),
        SYSTEM_MSG(4, "系统消息"),
        GET_DATA_MSG(5, "获取数据消息");

        private int code;
        private String desc;

        FirstLevelMsgType(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }


    @Getter
    public enum SecondLevelMsgType {

        /**
         * 具体消息类型
         * <p>
         * 1-101 撤回
         * <p>
         * 2-201 服务端接收到消息ack
         * 2-202 已读
         * 2-203 未读
         * <p>
         * 3-301 单聊信息
         * 3-302 重新发送
         * <p>
         * 4-401 系统消息
         *
         * -11 负数代表 发消息前的请求操作，-11 代表：获取一批消息id
         *
         */
        GET_MSG_IDS(-11, "获取一批消息id"),
        WITHDRAW(101, "撤回"),
        SERVER_RECEIVE_ACK(201, "服务端接收到消息ack"),
        READ(202, "已读"),
        UN_READ(203, "未读"),
        C2C(301, "单聊消息"),
        AGAIN(302, "重新发送"),
        SYSTEM(401, "系统消息");

        private int code;
        private String desc;

        SecondLevelMsgType(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }


}
