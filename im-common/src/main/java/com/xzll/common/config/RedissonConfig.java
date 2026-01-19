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
@ConditionalOnExpression("'${spring.data.redis.host:}' != ''")
public class RedissonConfig {

    // Spring Boot 3.x 新配置前缀
    @Value("${spring.data.redis.host:}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.timeout:10000}")
    private int timeout;

    @Value("${spring.data.redis.lettuce.pool.max-active:64}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:10}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
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
        var serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionPoolSize(maxActive)
                .setConnectionMinimumIdleSize(minIdle)
                // 连接超时
                .setConnectTimeout(timeout)
                // 命令执行超时（关键！）
                .setTimeout(timeout)
                // 空闲连接超时
                .setIdleConnectionTimeout(30000)
                // 重试配置
                .setRetryAttempts(5)
                .setRetryInterval(2000)
                // 禁用 ping 连接检测（关键！避免初始化超时）
                .setPingConnectionInterval(0)
                // 订阅连接池
                .setSubscriptionConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                // TCP 优化
                .setKeepAlive(true)
                .setTcpNoDelay(true);
        
        // 如果有密码，设置密码
        if (password != null && !password.isEmpty()) {
            serverConfig.setPassword(password);
            log.info("Redisson 已设置密码认证");
        } else {
            log.warn("Redisson 未配置密码，如果Redis需要密码认证会导致连接失败！");
        }
        
        log.info("正在创建 Redisson 客户端...");
        RedissonClient client = Redisson.create(config);
        log.info("Redisson 客户端创建成功！");
        return client;
    }
} 