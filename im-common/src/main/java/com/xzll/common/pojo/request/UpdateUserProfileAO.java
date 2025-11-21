package com.xzll.common.pojo.request;

import lombok.Data;

/**
 * 更新用户个人信息请求参数
 */
@Data
public class UpdateUserProfileAO {
    
    /**
     * 用户ID（必填）
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
     * 性别：0女，1男，-1未知
     */
    private Integer sex;
    
    /**
     * 头像URL
     */
    private String headImage;
}
