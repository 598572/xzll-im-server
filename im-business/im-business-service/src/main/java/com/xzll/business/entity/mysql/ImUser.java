package com.xzll.business.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/06/03 08:11:39
 * @Description: im用户表
 */
@Data
@TableName("im_user")
public class ImUser implements Serializable {

    private static final long serialVersionUID = -1L;

    /**
     * 主键
     */
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
     * 用户密码
     */
    private String password;

    /**
     * 用户全称
     */
    private String userFullName;

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
    private String email;

    /**
     * 0女 ，1男，-1 未知
     */
    private Integer sex;

    /**
     * 注册时的终端类型，1:android, 2:ios，3:小程序，4:web
     */
    private Integer registerTerminalType;

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
