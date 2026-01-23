package com.xzll.business.config.nacos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: MongoDB 配置类，从 Nacos 读取配置
 * 
 * 配置说明：
 * - uri: MongoDB 连接地址，格式为 mongodb://host:port/database
 * - database: 数据库名称
 * - 支持 Nacos 配置热更新
 */
@Setter
@Getter
@Component(value = "mongoDBNacosConfig")
@RefreshScope
@ConfigurationProperties(prefix = "im.mongodb")
public class MongoDBNacosConfig {

    /**
     * MongoDB 连接 URI
     * 格式: mongodb://host:port 或 mongodb://user:password@host:port
     */
    private String uri = "mongodb://localhost:27017";

    /**
     * 数据库名称
     */
    private String database = "im_message";

    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectTimeout = 10000;

    /**
     * 读取超时时间（毫秒）
     */
    private Integer readTimeout = 10000;

    /**
     * 最小连接池大小
     */
    private Integer minPoolSize = 5;

    /**
     * 最大连接池大小
     */
    private Integer maxPoolSize = 50;

    /**
     * 最大等待时间（毫秒）
     */
    private Integer maxWaitTime = 10000;
}
