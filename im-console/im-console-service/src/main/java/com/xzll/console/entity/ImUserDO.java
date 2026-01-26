package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: IM用户实体类
 */
@Data
@TableName("im_user")
public class ImUserDO {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户id 全局唯一
     */
    private String userId;
    
    /**
     * 用户账号 用于登录
     */
    private String userName;
    
    /**
     * 用户全称
     */
    private String userFullName;
    
    /**
     * 用户密码（后台不返回）
     */
    private String password;
    
    /**
     * 用户手机号
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
     * 注册时的终端类型：1:android, 2:ios, 3:小程序, 4:web
     */
    private Integer registerTerminalType;

    /**
     * 用户状态：0-正常，1-禁用
     */
    private Integer status;

    /**
     * 最后一次登录时间
     */
    private LocalDateTime lastLoginTime;
    
    /**
     * 注册时间
     */
    private LocalDateTime registerTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
