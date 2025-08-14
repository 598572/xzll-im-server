package com.xzll.auth.constant;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 认证相关常量
 */
public class AuthConstant {
    
    /**
     * 默认用户状态 - 启用
     */
    public static final Integer DEFAULT_USER_STATUS = 1;
    
    /**
     * 默认用户角色
     */
    public static final String DEFAULT_USER_ROLE = "ADMIN";
    
    /**
     * 默认性别 - 未知
     */
    public static final Integer DEFAULT_SEX = -1;
    
    /**
     * 默认注册终端类型 - Web端
     */
    public static final Integer DEFAULT_REGISTER_TERMINAL_TYPE = 4;
    
    /**
     * Token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";
    
    /**
     * 用户ID在JWT中的键名
     */
    public static final String JWT_USER_ID_KEY = "id";
    
    /**
     * 设备类型在JWT中的键名
     */
    public static final String JWT_DEVICE_TYPE_KEY = "device_type";
    
    /**
     * 用户名最小长度
     */
    public static final int USERNAME_MIN_LENGTH = 4;
    
    /**
     * 用户名最大长度
     */
    public static final int USERNAME_MAX_LENGTH = 20;
    
    /**
     * 密码最小长度
     */
    public static final int PASSWORD_MIN_LENGTH = 8;
    
    /**
     * 性别枚举
     */
    public enum Sex {
        UNKNOWN(-1, "未知"),
        FEMALE(0, "女"),
        MALE(1, "男");
        
        private final Integer code;
        private final String desc;
        
        Sex(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }
        
        public Integer getCode() {
            return code;
        }
        
        public String getDesc() {
            return desc;
        }
    }
    
    /**
     * 注册终端类型枚举
     */
    public enum RegisterTerminalType {
        ANDROID(1, "Android"),
        IOS(2, "iOS"),
        MINI_PROGRAM(3, "小程序"),
        WEB(4, "Web");
        
        private final Integer code;
        private final String desc;
        
        RegisterTerminalType(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }
        
        public Integer getCode() {
            return code;
        }
        
        public String getDesc() {
            return desc;
        }
    }
}
