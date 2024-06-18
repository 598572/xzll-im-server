package com.xzll.gateway.config.nacos;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
@ConfigurationProperties(prefix="im.ignore")
public class IgnoreUrlsConfig {

    private List<String> urls;


}
