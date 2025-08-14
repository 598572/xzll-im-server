package com.xzll.auth.domain;

import lombok.Data;
import com.xzll.common.constant.enums.ImTerminalType;

/**
 * @Author: hzz
 * @Date: 2025/8/14 17:30:00
 * @Description: 登出请求对象
 */
@Data
public class LogoutRequest {
    
    /**
     * 用户ID（必传）
     */
    private String userId;
    
    /**
     * 设备类型（必传）
     */
    private ImTerminalType deviceType;
} 