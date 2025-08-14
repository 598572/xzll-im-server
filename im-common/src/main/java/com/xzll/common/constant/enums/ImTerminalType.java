package com.xzll.common.constant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 
 * @Author: hzz
 * @Date:  2025/1/17 11:33:40
 * @Description: 
 */
public enum ImTerminalType {
    ANDROID(1, "android"),
    IOS(2, "ios"),
    MINI_PROGRAM(3, "小程序"),
    WEB(4, "web"),
    UNKNOWN(-1, "未知设备类型");

    private final int code;
    // 终端类型的描述
    private final String description;

    ImTerminalType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取对应的终端类型描述
     * @param code
     * @return
     */
    public static ImTerminalType fromCode(int code) {
        for (ImTerminalType terminalType : values()) {
            if (terminalType.getCode() == code) {
                return terminalType;
            }
        }
        return UNKNOWN;
    }
    
    /**
     * Jackson反序列化方法，根据code值创建枚举实例
     * 如果code无效，返回UNKNOWN枚举值，让Controller层处理校验
     * @param code 设备类型代码
     * @return 对应的枚举实例，如果code无效则返回UNKNOWN
     */
    @JsonCreator
    public static ImTerminalType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return fromCode(code.intValue());
    }
}
