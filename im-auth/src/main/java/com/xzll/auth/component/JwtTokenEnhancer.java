package com.xzll.auth.component;

import com.xzll.auth.constant.AuthConstant;
import com.xzll.auth.domain.SecurityUser;
import com.xzll.auth.util.DeviceTypeContext;
import com.xzll.common.constant.enums.ImTerminalType;
import com.xzll.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:06:10
 * @Description: jwt内容增强
 * <p>
 * 增强功能：
 * 1. 将用户ID添加到JWT中
 * 2. 将设备类型添加到JWT中（从OAuth2认证请求参数中获取）
 */
@Slf4j
@Component
public class JwtTokenEnhancer implements TokenEnhancer {
    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        Map<String, Object> info = new HashMap<>();
        
        // 验证用户ID
        if (securityUser.getId() == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        
        // 把用户ID设置到JWT中
        info.put(AuthConstant.JWT_USER_ID_KEY, securityUser.getId());
        
        // 从多个地方尝试获取设备类型信息
        Integer deviceTypeCode = null;
        
        try {
            // 方法1：从ThreadLocal中获取（刷新token时使用）
            ImTerminalType deviceTypeFromContext = DeviceTypeContext.getDeviceType();
            if (deviceTypeFromContext != null) {
                deviceTypeCode = deviceTypeFromContext.getCode();
                log.info("从ThreadLocal中获取到设备类型: {}", deviceTypeFromContext.getDescription());
            }
            
            // 方法2：如果ThreadLocal中没有，从OAuth2请求参数中获取（登录时使用）
            if (deviceTypeCode == null) {
                Map<String, String> requestParameters = authentication.getOAuth2Request().getRequestParameters();
                log.info("JwtTokenEnhancer请求参数:{}", JsonUtils.toJsonStr(requestParameters));
                
                String deviceTypeStr = requestParameters.get("device_type");
                if (StringUtils.isBlank(deviceTypeStr)) {
                    deviceTypeStr = requestParameters.get("deviceType");
                }
                
                if (deviceTypeStr != null) {
                    deviceTypeCode = Integer.valueOf(deviceTypeStr);
                    log.debug("从请求参数中获取到设备类型: {}", deviceTypeCode);
                }
            }
            
            // 设置设备类型到JWT中
            if (deviceTypeCode != null) {
                ImTerminalType deviceType = ImTerminalType.fromCode(deviceTypeCode);
                if (deviceType != null && deviceType != ImTerminalType.UNKNOWN) {
                    // 把设备类型设置到JWT中
                    info.put(AuthConstant.JWT_DEVICE_TYPE_KEY, deviceTypeCode);
                    log.info("将设备类型添加到JWT中: userId={}, deviceType={}", securityUser.getId(), deviceType.getDescription());
                } else {
                    log.warn("无效的设备类型: {}", deviceTypeCode);
                }
            } else {
                log.warn("未找到设备类型信息");
            }
        } catch (Exception e) {
            log.warn("获取设备类型失败", e);
        }
        
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(info);
        return accessToken;
    }
}
