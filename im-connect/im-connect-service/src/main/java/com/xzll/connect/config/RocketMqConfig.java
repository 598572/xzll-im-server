package com.xzll.connect.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;


/**
 * @Author: hzz
 * @Date: 2024/6/1 11:01:20
 * @Description: rocketMq配置
 */
@Setter
@Getter
@Component
@RefreshScope
@ConfigurationProperties(prefix = "im.rocket")
public class RocketMqConfig {

    /**
     * rocketMq地址
     */
    private String serverAddr;
    /**
     * 生产者组名称
     */
    private String producerGroupName;
    /**
     * 发送超时配置
     */
    private Integer sendTimeOut ;

}
