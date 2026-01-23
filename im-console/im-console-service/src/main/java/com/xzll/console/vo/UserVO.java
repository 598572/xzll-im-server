package com.xzll.console.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户信息VO（不包含敏感信息）
 */
@Data
public class UserVO {
    
    private Long id;
    
    /**
     * 用户id
     */
    private String userId;
    
    /**
     * 用户账号
     */
    private String userName;
    
    /**
     * 用户全称
     */
    private String userFullName;
    
    /**
     * 手机号（脱敏）
     */
    private String phone;
    
    /**
     * 用户头像
     */
    private String headImage;
    
    /**
     * 用户邮箱
     */
    private String eMail;
    
    /**
     * 性别：0女，1男，-1未知
     */
    private Integer sex;
    
    /**
     * 性别描述
     */
    private String sexDesc;
    
    /**
     * 注册终端类型
     */
    private Integer registerTerminalType;
    
    /**
     * 终端类型描述
     */
    private String terminalTypeDesc;
    
    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
    
    /**
     * 注册时间
     */
    private LocalDateTime registerTime;
    
    /**
     * 是否在线
     */
    private Boolean online;
    
    /**
     * 好友数量
     */
    private Long friendCount;
}
