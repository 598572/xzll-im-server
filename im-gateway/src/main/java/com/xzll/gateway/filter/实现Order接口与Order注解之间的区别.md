
# 实现Order接口和使用Order注解有什么不一样吗？

在 Spring 框架中，实现 Ordered 接口和使用 @Order 注解都是设置组件（如过滤器、拦截器等）执行顺序的方式。两者的效果是相同的，但它们的使用方式和适用场景有所不同。以下是详细的比较：

- 实现 Ordered 接口
    - 优点
类型安全：通过接口方法设置优先级，不会出现注解值类型错误的问题。
灵活性：可以在运行时动态计算或修改优先级。
    - 缺点
代码冗长：需要实现接口并提供 getOrder 方法。
不可读性：优先级信息分散在代码中，阅读代码时不如注解直观。

```java

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 前置处理逻辑
        System.out.println("GlobalFilter: Pre-processing logic");

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 后置处理逻辑
            System.out.println("GlobalFilter: Post-processing logic");
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // 优先级，数值越小优先级越高
    }
}
```
- 使用 @Order 注解
    - 优点
简洁明了：直接在类或方法上注解优先级，代码更加简洁易读。
方便配置：优先级信息集中在注解上，便于查看和修改。
    - 缺点
类型安全性较低：如果优先级值写错（如类型不匹配），编译器不会捕获错误。
灵活性较差：不能在运行时动态计算或修改优先级。


```java
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE) // 使用注解设置优先级
public class CustomGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 前置处理逻辑
        System.out.println("GlobalFilter: Pre-processing logic");

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 后置处理逻辑
            System.out.println("GlobalFilter: Post-processing logic");
        }));
    }
}
```
- 比较总结
    - 实现 Ordered 接口：
        - 灵活性：可以在运行时动态计算优先级。
        - 类型安全：避免注解值类型错误。
        - 冗长性：需要实现接口方法，代码较为冗长。
- 使用 @Order 注解：
    - 简洁性：代码更简洁，优先级信息直观。
    - 配置方便：优先级信息集中在注解上，便于查看和修改。
    - 灵活性不足：无法动态计算优先级。
- 适用场景
    - 实现 Ordered 接口： 
      - 需要动态计算或修改优先级。 
      - 需要在代码中严格控制执行顺序。
    - 使用 @Order 注解：
        - 优先级相对固定，修改不频繁。
        - 需要代码简洁易读。

选择哪种方式主要取决于具体需求和代码风格偏好。如果优先级是固定的且不需要动态计算，使用 @Order 注解更为简洁直观。如果优先级需要根据运行时条件动态计算或修改，实现 Ordered 接口则更为合适。