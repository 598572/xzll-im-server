package com.xzll.business.config;

import com.xzll.common.util.msgId.SnowflakeIdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: SnowflakeIdService配置类
 */
@Slf4j
@Configuration
public class SnowflakeIdConfig {

    @Bean("snowflakeIdService")
    public SnowflakeIdService snowflakeIdService() {
        log.info("【初始化】SnowflakeIdService - 群组服务模块");
        return new SnowflakeIdService();
    }
}
