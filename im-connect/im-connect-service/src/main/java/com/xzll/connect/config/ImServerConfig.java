package com.xzll.connect.config;

import com.xzll.common.util.msgId.MsgIdUtilsService;
import com.xzll.common.util.msgId.WorkerIdStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/5/30 13:27:55
 * @Description:
 */
@Slf4j
@Configuration
public class ImServerConfig {

    @Resource
    private Environment env;

    @Autowired
    private Map<String, WorkerIdStrategy> strategies;

    @Bean("msgIdUtilsService")
    public MsgIdUtilsService msgIdUtilsService() {
        String groupId = env.getProperty("spring.cloud.nacos.config.group");
        String strategyType = env.getProperty("im.msgId.strategyType");
        if (StringUtils.isBlank(strategyType)) {
            strategyType = "macAddressWorkerIdStrategy";
        }
        log.info("当前消息id的数据中心:{},workId生成策略:{}", groupId, strategyType);
        //根据配置选择策略
        WorkerIdStrategy strategy = strategies.get(strategyType);
        if (strategy == null) {
            throw new IllegalArgumentException("No such strategy: " + strategyType);
        }
        return new MsgIdUtilsService(strategy.getWorkerId(), groupId);
    }

}
