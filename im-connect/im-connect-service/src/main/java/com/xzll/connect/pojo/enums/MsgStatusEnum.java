package com.xzll.connect.pojo.enums;

import lombok.Getter;

/**
 * 消息状态枚举集合
 */
public interface MsgStatusEnum {


    @Getter
    public enum MsgReadStatus {

        UN_READ(0, "未读"),
        READED(1, "已读");

        private int code;
        private String desc;

        MsgReadStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }


    @Getter
    public enum MsgOfflineStatus {
        NO(0, "非离线"),
        YES(1, "离线");
        private int code;
        private String desc;

        MsgOfflineStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    @Getter
    public enum MsgWithdrawStatus {

        NO(0, "非撤回"),
        YES(1, "撤回");
        private int code;
        private String desc;

        MsgWithdrawStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    @Getter
    public enum MsgSendStatus {

        SERVER_RECEIVED(1, "消息已送达服务器"),
        //SENDED(2, "已发送至接收者"),
        SUCCESS(3, "发送成功"),
        FAIL(4, "发送失败");
        private int code;
        private String desc;

        MsgSendStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

}
