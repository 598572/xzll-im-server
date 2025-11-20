package com.xzll.common.pojo.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户个人信息响应对象
 */
@Data
public class UserProfileVO {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户名/昵称
     */
    private String userName;
    
    /**
     * 用户全名
     */
    private String userFullName;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 用户头像URL
     */
    private String headImage;
    
    /**
     * 性别：0女，1男，-1未知
     */
    private Integer sex;
    
    /**
     * 注册时间
     */
    private LocalDateTime registerTime;
    
    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
}
