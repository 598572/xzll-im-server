package com.xzll.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages  = {"com.xzll"})
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class IMAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMAuthServiceApplication.class, args);
    }

}
