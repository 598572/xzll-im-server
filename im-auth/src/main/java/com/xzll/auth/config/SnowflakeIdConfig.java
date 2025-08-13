package com.xzll.auth.config;

import com.xzll.common.util.msgId.SnowflakeIdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: SnowflakeIdService配置类
 */
@Slf4j
@Configuration
public class SnowflakeIdConfig {

    @Bean("snowflakeIdService")
    public SnowflakeIdService snowflakeIdService() {
        log.info("初始化SnowflakeIdService");
        return new SnowflakeIdService();
    }
}
