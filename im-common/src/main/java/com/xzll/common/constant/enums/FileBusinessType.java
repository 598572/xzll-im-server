package com.xzll.common.constant.enums;

/**
 * 业务类型枚举
 */
public enum FileBusinessType {
    AVATAR("avatar"),           // 头像
    C2C_CHAT("c2c_chat"), // 单聊文件
    GROUP_CHAT("group_chat");     // 群聊文件
    
    private final String path;
    
    FileBusinessType(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
}