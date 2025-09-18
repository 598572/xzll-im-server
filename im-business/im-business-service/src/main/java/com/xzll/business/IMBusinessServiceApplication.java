package com.xzll.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages  = {"com.xzll"})
@EnableTransactionManagement
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class IMBusinessServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IMBusinessServiceApplication.class);
        application.run(args);
    }

}
