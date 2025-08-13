package com.xzll.common.constant.enums;

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
    WEB(4, "web");

    private final int code;
    // 终端类型的描述
    private final String description;

    ImTerminalType(int code, String description) {
        this.code = code;
        this.description = description;
    }

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
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
