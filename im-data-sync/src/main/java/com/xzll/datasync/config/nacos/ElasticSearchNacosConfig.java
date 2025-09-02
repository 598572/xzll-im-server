package com.xzll.datasync.config.nacos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: ES Nacos 配置类
 */
@Setter
@Getter
@Component(value = "elasticSearchNacosConfig")
@RefreshScope
@ConfigurationProperties(prefix = "im.elasticsearch")
public class ElasticSearchNacosConfig {

    /**
     * es集群地址
     */
    private List<String> uris;

} 