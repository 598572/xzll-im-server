package com.xzll.business.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.xzll.business.config.nacos.MongoDBNacosConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: MongoDB 配置类
 * 
 * 功能说明：
 * - 创建 MongoDB 客户端连接
 * - 配置连接池参数
 * - 创建 MongoTemplate 用于数据操作
 * 
 * 开关说明：
 * - mongodb.enabled=true 时启用（默认启用）
 * - mongodb.enabled=false 时禁用
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mongodb.enabled", havingValue = "true", matchIfMissing = true)
public class MongoDBConfig {

    @Resource
    private MongoDBNacosConfig mongoDBNacosConfig;

    /**
     * 创建 MongoDB 客户端
     * 配置连接池和超时参数
     */
    @Bean
    public MongoClient mongoClient() {
        String uri = mongoDBNacosConfig.getUri();
        String deploymentMode = mongoDBNacosConfig.getDeploymentMode();

        log.info("正在创建 MongoDB 连接, URI: {}, 部署模式: {}",
                uri.replaceAll(":[^:@]+@", ":***@"), deploymentMode);

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                // 连接池配置
                .applyToConnectionPoolSettings(builder -> builder
                        .minSize(mongoDBNacosConfig.getMinPoolSize())
                        .maxSize(mongoDBNacosConfig.getMaxPoolSize())
                        .maxWaitTime(mongoDBNacosConfig.getMaxWaitTime(), TimeUnit.MILLISECONDS)
                        // ✅ 维护连接，避免频繁重建
                        .maintenanceInitialDelay(mongoDBNacosConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                        .maintenanceFrequency(60000, TimeUnit.MILLISECONDS)  // 每60秒维护一次
                )
                // 超时配置
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(mongoDBNacosConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                        .readTimeout(mongoDBNacosConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                );

        // ✅ 根据部署模式动态配置SDAM（仅在非单机模式下应用）
        if (!"standalone".equalsIgnoreCase(deploymentMode)) {
            // 集群模式：启用SDAM，支持自动发现和故障转移
            log.info("📌 使用集群模式（{}）：启用SDAM服务发现", deploymentMode);
            settingsBuilder.applyToClusterSettings(builder -> builder
                    .serverSelectionTimeout(mongoDBNacosConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
            );
        } else {
            // 单机模式：使用默认配置
            log.info("📌 使用单机模式");
        }

        MongoClient client = MongoClients.create(settingsBuilder.build());
        log.info("MongoDB 连接创建成功, 数据库: {}", mongoDBNacosConfig.getDatabase());

        return client;
    }

    /**
     * 创建 MongoDB 数据库工厂
     */
    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, mongoDBNacosConfig.getDatabase());
    }

    /**
     * 创建 MongoTemplate
     * 用于执行 MongoDB 数据操作
     * 
     * 特殊配置：
     * - 去除 _class 字段（存储时不添加类型信息，节省空间）
     */
    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        // 创建映射上下文
        MongoMappingContext mappingContext = new MongoMappingContext();
        
        // 创建转换器，去除 _class 字段
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDatabaseFactory), 
                mappingContext
        );
        // 设置为 null 表示不存储 _class 字段
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        
        log.info("MongoTemplate 创建成功");
        return template;
    }
}
