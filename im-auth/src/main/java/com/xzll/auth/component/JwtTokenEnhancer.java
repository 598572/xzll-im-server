package com.xzll.auth.component;

import com.xzll.auth.constant.AuthConstant;
import com.xzll.auth.domain.SecurityUser;
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
 */
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
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(info);
        return accessToken;
    }
}
