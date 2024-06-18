package com.xzll.common.constant;

import lombok.Getter;

/**
 * 消息格式枚举
 */
@Getter
public enum MsgFormatEnum {

    TEXT_MSG(1, "文本消息"),
    VOICE_MSG(2, "语音条消息"),
    LOCATION_MSG(3, "地理位置消息");

    private int code;
    private String desc;

    MsgFormatEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
