package com.xzll.datasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfiguration;

/**
 * 数据同步服务启动类
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@SpringBootApplication(scanBasePackages = {"com.xzll.datasync"},
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class,
                RedissonAutoConfiguration.class,
                ElasticsearchDataAutoConfiguration.class,
                ElasticsearchRepositoriesAutoConfiguration.class
        })
public class IMDataSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMDataSyncApplication.class, args);
    }
}
