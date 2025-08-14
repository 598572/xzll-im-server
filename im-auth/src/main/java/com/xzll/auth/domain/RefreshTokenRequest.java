package com.xzll.auth.domain;

import lombok.Data;
import com.xzll.common.constant.enums.ImTerminalType;

/**
 * @Author: hzz
 * @Date: 2025/8/14 17:30:00
 * @Description: 刷新token请求对象
 */
@Data
public class RefreshTokenRequest {
    
    /**
     * 刷新令牌（必传）
     */
    private String refreshToken;
    
    /**
     * 设备类型（必传）
     */
    private ImTerminalType deviceType;
} 