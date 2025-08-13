package com.xzll.connect;

import com.xzll.common.config.DubboNetworkInitializer;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


//配置此注解后，无需在 ConfigurationProperties 所在的类中 再添加 @Component 或者 @Configuration注解了
@ConfigurationPropertiesScan

@SpringBootApplication(scanBasePackages = {"com.xzll"}, exclude = {DataSourceAutoConfiguration.class})
@EnableDubbo
public class IMConnectServiceApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IMConnectServiceApplication.class);
        application.addInitializers(new DubboNetworkInitializer());
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }

}
