package com.xzll.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @Author: hzz
 * @Date: 2024/6/10
 * @Description: 密码编码器配置类
 * 
 * 独立配置，避免与 WebSecurityConfig 形成循环依赖
 * （UserServiceImpl 依赖 PasswordEncoder，WebSecurityConfig 依赖 UserServiceImpl）
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
