package com.xzll.common.constant;

import lombok.Getter;

/**
 * 消息状态枚举集合
 */
public interface MsgStatusEnum {


    /**
     * 最新设计的消息状态
     */
    @Getter
    public enum MsgStatus {
        /**
         * 消息状态
         * -1：发送失败：客户端展示状态：（发送失败），发送失败的原因较多
         * 更新发送失败状态的设计：
         * 1.将使用延迟队列 如果接收者在线且 一定时间内没收到接收者的ack，将更新消息状态为发送失败，并推给发送者 发送失败 的提示
         * 2.发送成功后将消息id放入redis 如果收到ack则删除，否则在一定时间内，拿到redis中所有的发送失败的msgId 将其状态更新至db(但需要判断其上一状态一定是到达服务器)
         * <p>
         * 1：到达服务器；  客户端展示状态：（发送中）
         * 2：离线：  客户端展示状态：（未读）
         * 3：未读：客户端展示状态：（未读）
         * 4：已读：客户端展示状态：（已读）
         *
         */
        FAIL(-1, "发送失败"),
        SERVER_RECEIVED(1, "消息已送达服务器"),
        OFF_LINE(2, "离线"),
        UN_READ(3, "未读"),
        READED(4, "已读");

        private int code;
        private String desc;

        MsgStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        /**
         * 根据 code 查找对应的 name
         *
         * @param code 状态码
         * @return 对应的描述，如果没有找到对应的状态码，则返回 null
         */
        public static String getNameByCode(int code) {
            for (MsgStatus status : MsgStatus.values()) {
                if (status.getCode() == code) {
                    return status.getDesc();
                }
            }
            return null;
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

}
