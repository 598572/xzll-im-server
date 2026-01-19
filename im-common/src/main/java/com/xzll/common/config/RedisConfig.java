package com.xzll.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @Author: hzz
 * @Date: 2024/5/30 13:27:55
 * @Description: Redis配置类
 * 支持 Spring Boot 3.x (spring.data.redis.*) 和 2.x (spring.redis.*) 配置前缀
 */
@Slf4j
@Setter
@Getter
@Configuration
@ConditionalOnExpression("'${spring.data.redis.host:}' != ''")
public class RedisConfig {

    // Spring Boot 3.x 新配置前缀
    @Value("${spring.data.redis.database:0}")
    private int database;
    
    @Value("${spring.data.redis.host:}")
    private String host;
    
    @Value("${spring.data.redis.port:6379}")
    private int port;
    
    @Value("${spring.data.redis.password:}")
    private String password;
    
    @Value("${spring.data.redis.timeout:10000}")
    private int timeout;
    
    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int maxActive;
    
    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;
    
    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("\n========== RedisConfig 配置信息 ==========" +
                "\n  host: {}" +
                "\n  port: {}" +
                "\n  database: {}" +
                "\n  password: {}" +
                "\n=======================================",
                host, port, database,
                (password != null && !password.isEmpty()) ? "******(已配置)" : "(无密码)");
        
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(this.getHost());
        configuration.setPort(this.getPort());
        configuration.setDatabase(this.getDatabase());
        if (StringUtils.isBlank(this.getPassword())) {
            configuration.setPassword(RedisPassword.none());
        } else {
            configuration.setPassword(this.getPassword());
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean("redisTemplate")
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        //设置key 序列化的方式
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);

        //设置value 序列化的方式
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        serializer.setObjectMapper(objectMapper);

        redisTemplate.setValueSerializer(serializer);

        //设置 hash key value 序列化的方式
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        // 禁用默认序列化器
        redisTemplate.setEnableDefaultSerializer(false);
        redisTemplate.setDefaultSerializer(null);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }


    @Bean(name = "secondaryRedisTemplate")
    public RedisTemplate<String, Object> secondaryRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        Jackson2JsonRedisSerializer<?> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 专门用于字符串操作的RedisTemplate
     * 保存什么就存什么，不进行JSON序列化
     */
    @Bean(name = "myStringRedisTemplate")
    @ConditionalOnMissingBean(name = "myStringRedisTemplate")
    public RedisTemplate<String, String> myStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        // 使用StringRedisSerializer，保持字符串原样
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        
        // 设置所有序列化器都为StringRedisSerializer
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(stringRedisSerializer);
        
        // 重要：禁用默认序列化器，避免干扰
        redisTemplate.setDefaultSerializer(stringRedisSerializer);
        
        // 设置字符串编码
        redisTemplate.setStringSerializer(stringRedisSerializer);
        
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

}
