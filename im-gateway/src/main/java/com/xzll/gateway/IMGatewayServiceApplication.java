package com.xzll.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class IMGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMGatewayServiceApplication.class, args);
    }

}
