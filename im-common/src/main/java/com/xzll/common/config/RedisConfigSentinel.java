package com.xzll.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.*;

/**
 * @Author: hzz
 * @Date: 2024/5/30 13:27:55
 * @Description: 哨兵模式，本机mac远程连接的话 不好使，无法修改哨兵返回的redis ip 所以本机启动项目时 使用另一个类 redisConfig即可，部署到服务上时  可以连哨兵集群
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "spring.redis")
public class RedisConfigSentinel {


    private Sentinel sentinel = new Sentinel();

    @Setter
    @Getter
    public class Sentinel {
        private String nodes;
        private String master;
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


    //-------------------------------------尝试修改哨兵返回的redis主机ip 但是不成功，故放弃，本机使用单机模式连接redis吧


//    /**
//     * 获取哨兵的地址和端口
//     *
//     * @return
//     */
//    private Set<String> getSentinels() {
//        Set<String> redisNodes = new HashSet<>();
//        // 分割节点字符串
//        String[] nodes = sentinel.nodes.split(",");
//        // 遍历每个节点并创建 RedisNode 对象
//        for (String node : nodes) {
//            redisNodes.add(node);
//        }
//        return redisNodes;
//    }

//
//    /**
//     * 正在检查是否存在 SSH 进程...
//     * 检测到 SSH 进程 (PID: 8434)，正在终止...
//     * SSH 进程已终止。
//     * 正在创建主连接...
//     * root@9xxxxxxxx's password:
//     * 主连接已创建成功。
//     * 正在打开 Redis 哨兵隧道 (26379)...
//     * Redis 哨兵隧道 (26379) 已成功打开。
//     * COMMAND  PID USER   FD   TYPE             DEVICE SIZE/OFF NODE NAME
//     * ssh     8703  hzz    8u  IPv6 0xfb55eea3aa52ad3f      0t0  TCP localhost:26379 (LISTEN)
//     * ssh     8703  hzz    9u  IPv4 0xfb55eea3a386bf77      0t0  TCP localhost:26379 (LISTEN)
//     * <p>
//     * 正在打开 Redis 哨兵隧道 (36379)...
//     * Redis 哨兵隧道 (36379) 已成功打开。
//     * COMMAND  PID USER   FD   TYPE             DEVICE SIZE/OFF NODE NAME
//     * ssh     8703  hzz   10u  IPv6 0xfb55eea3aa52b9ff      0t0  TCP localhost:36379 (LISTEN)
//     * ssh     8703  hzz   11u  IPv4 0xfb55eea3a978ac67      0t0  TCP localhost:36379 (LISTEN)
//     * <p>
//     * 正在打开 Redis 哨兵隧道 (46379)...
//     * Redis 哨兵隧道 (46379) 已成功打开。
//     * COMMAND  PID USER   FD   TYPE             DEVICE SIZE/OFF NODE NAME
//     * ssh     8703  hzz   12u  IPv6 0xfb55eea3aa52c05f      0t0  TCP localhost:46379 (LISTEN)
//     * ssh     8703  hzz   13u  IPv4 0xfb55eea3a8e8f817      0t0  TCP localhost:46379 (LISTEN)
//     *
//     * @return
//     */
////    @Bean
////    @Primary
////    public LettuceConnectionFactory redisConnectionFactory() {
////        // 创建哨兵配置
////        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration(masterName, getSentinels());
////        // 创建连接工厂
////        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(sentinelConfig);
////        //验证工厂中配置的属性是否正确。如果有任何配置不正确或缺失，该方法将抛出异常
////        lettuceConnectionFactory.afterPropertiesSet();
////
////        // 获取当前主节点信息 并替换主节点的 IP 地址为本地（127.0.0.1）地址，原因是已经做了端口转发
//////        RedisNode masterNode = sentinelConfig.getSentinels().stream()
//////                .findFirst() // 这里假设我们使用第一个哨兵的配置
//////                .map(redisNode -> new RedisNode("127.0.0.1", redisNode.getPort()))
//////                .orElseThrow(() -> new IllegalArgumentException("No sentinel nodes found"));
////
////        // 替换主节点的 IP 为本地地址
//////        sentinelConfig.getMaster()
//////        RedisNode newMasterNode = new RedisNode(localHost, masterNode.getPort());
//////
//////        // 设置替换后的主节点
//////        sentinelConfig.setMaster(masterNode);
//////        lettuceConnectionFactory.initConnection();
////
////
////        RedisURI redisURI = RedisURI.builder()
////                .withHost("127.0.0.1")  // 强制使用本地地址
////                .withPort(6379)          // 使用 Redis 端口
////                .build();
////
////        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisURI);
////        factory.setValidateConnection(true);  // 校验连接是否有效
////
////        return lettuceConnectionFactory;
////    }

}
