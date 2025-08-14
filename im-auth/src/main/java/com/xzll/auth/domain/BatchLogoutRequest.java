package com.xzll.auth.domain;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2025/8/14 21:00:00
 * @Description: 批量登出请求对象
 */
@Data
public class BatchLogoutRequest {
    
    /**
     * 用户ID（必传）
     */
    private String userId;
} 