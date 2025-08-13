package com.xzll.auth.domain;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2025/8/13 20:10:00
 * @Description: Token验证请求对象
 */
@Data
public class TokenRequest {
    
    /**
     * JWT token
     */
    private String token;
} 