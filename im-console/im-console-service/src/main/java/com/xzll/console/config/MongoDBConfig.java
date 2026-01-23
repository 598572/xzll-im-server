package com.xzll.console.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.xzll.console.config.nacos.MongoDBNacosConfig;
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
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mongodb.enabled", havingValue = "true", matchIfMissing = true)
public class MongoDBConfig {

    @Resource
    private MongoDBNacosConfig mongoDBNacosConfig;

    /**
     * 创建 MongoDB 客户端
     */
    @Bean
    public MongoClient mongoClient() {
        String uri = mongoDBNacosConfig.getUri();
        
        log.info("正在创建 MongoDB 连接, URI: {}", uri.replaceAll(":[^:@]+@", ":***@"));
        
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToConnectionPoolSettings(builder -> builder
                        .minSize(mongoDBNacosConfig.getMinPoolSize())
                        .maxSize(mongoDBNacosConfig.getMaxPoolSize())
                )
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(mongoDBNacosConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                        .readTimeout(mongoDBNacosConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                )
                .build();
        
        MongoClient client = MongoClients.create(settings);
        log.info("MongoDB 连接创建成功, 数据库: {}", mongoDBNacosConfig.getDatabase());
        
        return client;
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, mongoDBNacosConfig.getDatabase());
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        MongoMappingContext mappingContext = new MongoMappingContext();
        
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(mongoDatabaseFactory), 
                mappingContext
        );
        // 去除 _class 字段
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        
        MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
        
        log.info("MongoTemplate 创建成功");
        return template;
    }
}
