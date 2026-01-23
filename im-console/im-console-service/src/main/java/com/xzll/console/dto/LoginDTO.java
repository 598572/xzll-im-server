package com.xzll.console.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求DTO
 *
 * @author xzll
 */
@Data
public class LoginDTO {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码（可选）
     */
    private String captcha;

    /**
     * 验证码Key（可选）
     */
    private String captchaKey;
}
