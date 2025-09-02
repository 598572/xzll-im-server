package com.xzll.datasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfiguration;

/**
 * 数据同步服务启动类
 * @Author: hzz
 * @Date: 2024/12/20
 */
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    RedissonAutoConfiguration.class
})
public class DataSyncApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DataSyncApplication.class, args);
    }
}
