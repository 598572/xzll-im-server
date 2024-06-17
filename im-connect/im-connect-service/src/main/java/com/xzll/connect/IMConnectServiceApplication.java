package com.xzll.connect;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages  = {"com.xzll"})
@EnableDubbo
public class IMConnectServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMConnectServiceApplication.class, args);
    }

}
