package com.xzll.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
 * @Description:
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "spring.redis")
public class RedisConfig {

    private int database;
    private String host;
    private int port;
    private String password;
    private int timeout;
    private Pool pool = new Pool();

    @Setter
    @Getter
    public static class Pool {
        private int maxActive;
        private int maxIdle;
        private int minIdle;
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
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
