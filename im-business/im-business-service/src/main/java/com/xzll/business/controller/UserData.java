package com.xzll.business.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @Author: hzz
 * @Date:  2025/1/20 13:39:00
 * @Description: 
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserData {

    private String name;
    private String age;
    private String phone;
    private String address;
    private String email;
    private String backAccount;
    private String password;



}
