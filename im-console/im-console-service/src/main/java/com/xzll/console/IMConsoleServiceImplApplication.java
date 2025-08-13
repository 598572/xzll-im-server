package com.xzll.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages  = {"com.xzll"})
public class IMConsoleServiceImplApplication {

    public static void main(String[] args) {
        SpringApplication.run(IMConsoleServiceImplApplication.class, args);
    }

}
