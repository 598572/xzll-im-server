package com.xzll.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


@SpringBootApplication(scanBasePackages  = {"com.xzll"})//保证扫到common包 一些公共配置无需每一个服务都配一遍 如redis
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class IMGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMGatewayServiceApplication.class, args);
    }

}
