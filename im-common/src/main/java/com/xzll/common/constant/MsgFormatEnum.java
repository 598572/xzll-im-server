package com.xzll.common.constant;

import lombok.Getter;

/**
 * 消息格式枚举
 * 
 * @Author: hzz
 * @Date: 2025/11/19
 */
@Getter
public enum MsgFormatEnum {

    /** 文本消息 */
    TEXT_MSG(1, "文本消息"),
    
    /** 语音消息 */
    VOICE_MSG(2, "语音条消息"),
    
    /** 地理位置消息 */
    LOCATION_MSG(3, "地理位置消息"),
    
    /** 图片消息 */
    IMAGE_MSG(4, "图片消息"),
    
    /** 视频消息 */
    VIDEO_MSG(5, "视频消息"),
    
    /** 文件消息 */
    FILE_MSG(6, "文件消息"),
    
    /** 表情消息 */
    EMOJI_MSG(7, "表情消息"),
    
    /** 链接消息 */
    LINK_MSG(8, "链接消息"),
    
    /** 系统消息 */
    SYSTEM_MSG(9, "系统消息"),
    
    /** 撤回消息 */
    RECALL_MSG(10, "撤回消息");

    private final int code;
    private final String desc;

    MsgFormatEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     * 
     * @param code 消息格式代码
     * @return 对应的枚举，如果不存在返回null
     */
    public static MsgFormatEnum getByCode(int code) {
        for (MsgFormatEnum format : values()) {
            if (format.code == code) {
                return format;
            }
        }
        return null;
    }

    /**
     * 根据code获取描述
     * 
     * @param code 消息格式代码
     * @return 描述信息，如果不存在返回"未知消息格式"
     */
    public static String getDescByCode(int code) {
        MsgFormatEnum format = getByCode(code);
        return format != null ? format.desc : "未知消息格式";
    }

    /**
     * 校验code是否有效
     * 
     * @param code 消息格式代码
     * @return true=有效，false=无效
     */
    public static boolean isValid(int code) {
        return getByCode(code) != null;
    }
}
