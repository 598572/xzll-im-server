package com.xzll.common.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Redisson配置类
 * 用于配置Redisson客户端
 * 支持3.x (spring.data.redis.*) 配置前缀
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${spring.data.redis.host:}' != '' or '${spring.redis.host:}' != ''")
public class RedissonConfig {

    // 同时支持新旧两种配置前缀
    @Value("${spring.data.redis.host:${spring.redis.host:localhost}}")
    private String host;

    @Value("${spring.data.redis.port:${spring.redis.port:6379}}")
    private int port;

    @Value("${spring.data.redis.database:${spring.redis.database:0}}")
    private int database;

    @Value("${spring.data.redis.password:${spring.redis.password:}}")
    private String password;

    @Value("${spring.data.redis.timeout:${spring.redis.timeout:10000}}")
    private int timeout;

    @Value("${spring.data.redis.lettuce.pool.max-active:${spring.redis.lettuce.pool.max-active:64}}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:${spring.redis.lettuce.pool.max-idle:10}}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:${spring.redis.lettuce.pool.min-idle:0}}")
    private int minIdle;

    @Bean
    @Primary
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        // 打印配置信息用于调试
        log.info("\n========== Redisson 配置信息 ==========" +
                "\n  host: {}" +
                "\n  port: {}" +
                "\n  database: {}" +
                "\n  password: {}" +
                "\n  timeout: {}" +
                "\n=======================================",
                host, port, database,
                (password != null && !password.isEmpty()) ? "******(已配置)" : "(无密码)",
                timeout);

        Config config = new Config();
        
        // 单机模式配置
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionPoolSize(maxActive)
                .setConnectionMinimumIdleSize(minIdle)
                .setConnectTimeout(timeout)
                .setIdleConnectionTimeout(10000)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setKeepAlive(true)
                .setTcpNoDelay(true);
        
        // 如果有密码，设置密码
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
            log.info("Redisson 已设置密码认证");
        } else {
            log.warn("Redisson 未配置密码，如果Redis需要密码认证会导致连接失败！");
        }
        
        return Redisson.create(config);
    }
} 