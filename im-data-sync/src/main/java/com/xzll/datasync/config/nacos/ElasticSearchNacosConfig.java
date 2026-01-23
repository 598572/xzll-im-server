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
 * 
 * 配置说明：
 * - uris: ES集群地址列表
 * - syncEnabled: ES同步开关（默认false，不同步）
 * 
 * 当 syncEnabled=false 时，消息不会写入ES，仅记录日志
 * 当 syncEnabled=true 时，消息会同步写入ES
 */
@Setter
@Getter
@Component(value = "elasticSearchNacosConfig")
@RefreshScope
@ConfigurationProperties(prefix = "im.elasticsearch")
public class ElasticSearchNacosConfig {

    /**
     * ES集群地址
     */
    private List<String> uris;

    /**
     * ES同步开关
     * true: 开启ES同步，消息会写入ES
     * false: 关闭ES同步，消息不写入ES（默认值）
     * 
     * 注意：支持 Nacos 配置热更新，可在运行时动态开启/关闭
     */
    private Boolean syncEnabled = false;
} 