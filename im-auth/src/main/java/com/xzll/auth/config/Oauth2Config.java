package com.xzll.auth.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:49:20
 * @Description: 白名单配置
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Component
@RefreshScope
@ConfigurationProperties(prefix="im.oauth2")
public class Oauth2Config {

    /**
     * 基础配置
     */
    private String clientId;
    private List<String> scopes;
    private String password;
    private List<String> authorizedGrantTypes;

    /**
     * jwt相关
     */
    private String jwtFile;
    private String jwtPassword;
    private String jwtAlias;

    /**
     * 用户token 的过期时间
     */
    private Integer tokenTimeOut;
    /**
     * 刷新时间 一般很久
     */
    private Integer refreshToken;




}
