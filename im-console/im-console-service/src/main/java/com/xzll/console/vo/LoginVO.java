package com.xzll.console.vo;

import lombok.Data;

/**
 * 登录响应VO
 *
 * @author xzll
 */
@Data
public class LoginVO {

    /**
     * JWT Token
     */
    private String token;

    /**
     * 管理员ID
     */
    private String adminId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * Token过期时间（毫秒）
     */
    private Long expiresIn;
}
