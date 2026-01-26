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
 *       syncEnabled: true  # ES同步开关（默认关闭）
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
     * ES同步开关
     * true: 启用ES，查询优先使用ES
     * false: 禁用ES，查询使用MongoDB
     */
    private Boolean syncEnabled = false;

    /**
     * ES集群地址列表
     * 支持多节点配置，格式: http://host:port
     */
    private List<String> uris;

}
