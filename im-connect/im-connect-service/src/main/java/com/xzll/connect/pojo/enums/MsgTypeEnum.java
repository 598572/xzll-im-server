package com.xzll.connect.pojo.enums;

import lombok.Getter;

public interface MsgTypeEnum {


    @Getter
    public enum FirstLevelMsgType {

        /**
         * 消息类型
         * 1、指令消息（撤回）
         * 2、通知消息（服务端接收消息后的ack、已读）
         * 3、聊天消息（单聊、重新发送）
         * 4、系统消息（由系统发送的消息）
         */

        COMMAND_MSG(1, "指令消息"),
        ACK_MSG(2, "ack消息"),
        CHAT_MSG(3, "聊天消息"),
        SYSTEM_MSG(4, "系统消息");

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
         */
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
