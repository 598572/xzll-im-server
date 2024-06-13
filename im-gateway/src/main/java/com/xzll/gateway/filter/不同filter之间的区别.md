

# GlobalFilter 和 WebFilter 之间有什么区别？
在 Spring Cloud Gateway 项目中，GlobalFilter 和 WebFilter 都可以用来处理请求和响应，但它们有不同的用途和使用场景。以下是两者的详细区别：

1. GlobalFilter
   - 定义和用途
   GlobalFilter 是 Spring Cloud Gateway 特有的过滤器接口。
   用于在网关层面上处理所有通过网关的请求。
   可以在请求被路由到具体服务之前或之后进行处理。
   - 使用场景
   适用于需要全局处理所有通过网关的请求的场景。
   常用于日志记录、安全验证、请求修改、响应修改等操作。
   示例

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
2. WebFilter
   - 定义和用途
   WebFilter 是 Spring WebFlux 提供的过滤器接口。
   用于在 WebFlux 应用程序中处理请求。
   可以在请求处理的任何阶段进行拦截。
   - 使用场景
   适用于需要在 WebFlux 层面上处理请求的场景，而不仅仅是在网关层面。
   可以用于任何基于 WebFlux 的应用程序，而不仅限于 Spring Cloud Gateway。
```java   


import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CustomWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 前置处理逻辑
        System.out.println("WebFilter: Pre-processing logic");

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 后置处理逻辑
            System.out.println("WebFilter: Post-processing logic");
        }));
    }
}
```

主要区别

- 适用范围：
GlobalFilter 仅适用于 Spring Cloud Gateway，用于处理所有通过网关的请求。
WebFilter 适用于任何 WebFlux 应用程序，可以在整个应用程序中使用。
- 处理层级：
GlobalFilter 在请求到达具体服务之前或之后处理请求，主要用于网关级别的全局处理。
WebFilter 可以在请求的任何阶段进行拦截，适用范围更广。
- 优先级设置： GlobalFilter 通过实现 Ordered 接口来设置优先级。
WebFilter 也可以通过实现 Ordered 接口来设置优先级，但通常在应用程序中通过配置实现。
- 使用场景： GlobalFilter 适用于网关层面的统一处理，如认证、授权等。
WebFilter 适用于应用程序的任意处理阶段，如跨域处理、请求修改、响应修改、日志记录，链路信息设置 等。
  
- 选择使用 ：
    - 如果你的过滤逻辑是针对所有通过网关的请求，使用 GlobalFilter。
    - 如果你的过滤逻辑是针对 WebFlux 应用程序的请求处理，使用 WebFilter。
通过以上解释和示例，你可以根据具体需求选择合适的过滤器来实现请求和响应的处理逻辑。
