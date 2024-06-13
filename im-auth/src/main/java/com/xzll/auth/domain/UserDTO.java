package com.xzll.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/11 11:05:14
 * @Description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class UserDTO{
    private Long id;
    private String username;
    private String password;
    private Integer status;
    private List<String> roles;

}
