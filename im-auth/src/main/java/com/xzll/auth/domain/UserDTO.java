package com.xzll.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO{
    private Long id;
    private String userName;
    private String password;
    private Integer status;
    private List<String> roles;

    /**
     * 用户手机号
     */
    private String phone;
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



}
