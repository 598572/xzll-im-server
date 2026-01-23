package com.xzll.console.config.nacos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ES配置从Nacos读取
 * 配置前缀: im.elasticsearch
 * 配置示例:
 *   im:
 *     elasticsearch:
 *       uris:
 *         - http://192.168.1.131:9200
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Setter
@Getter
@Component(value = "elasticSearchNacosConfig")
@RefreshScope
@ConfigurationProperties(prefix = "im.elasticsearch")
public class ElasticSearchNacosConfig {

    /**
     * ES集群地址列表
     * 支持多节点配置，格式: http://host:port
     */
    private List<String> uris;

}
