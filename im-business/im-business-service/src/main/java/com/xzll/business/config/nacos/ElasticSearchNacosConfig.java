package com.xzll.business.config.nacos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/14 14:32:54
 * @Description:
 */
@Setter
@Getter
@Component
@RefreshScope
@ConfigurationProperties(prefix = "im.elasticsearch")
public class ElasticSearchNacosConfig {

    /**
     * es集群地址
     */
    private List<String> uris;

}
