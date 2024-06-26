package com.xzll.business;

import com.xzll.common.config.DubboNetworkInitializer;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages  = {"com.xzll"})
@EnableDubbo
@EnableTransactionManagement
public class IMBusinessServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IMBusinessServiceApplication.class);
        application.addInitializers(new DubboNetworkInitializer());
        application.run(args);
    }

}
