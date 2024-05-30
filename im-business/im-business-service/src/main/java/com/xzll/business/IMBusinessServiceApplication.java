package com.xzll.business;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class IMBusinessServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMBusinessServiceApplication.class, args);
    }

}
