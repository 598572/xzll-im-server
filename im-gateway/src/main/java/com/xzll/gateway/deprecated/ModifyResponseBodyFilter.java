//package com.xzll.gateway.config;
//
//import com.google.gson.Gson;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.cloud.gateway.filter.GatewayFilter;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
//import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
//import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
//import org.springframework.cloud.gateway.route.RouteLocator;
//import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//import java.util.Map;
//
///**
// * @Author: hzz
// * @Date: 2024/6/16 13:36:23
// * @Description:
// */
//@Component
//@Order(value = Ordered.HIGHEST_PRECEDENCE)
//public class ModifyResponseBodyFilter implements GatewayFilter {
//    private final Gson gson = new Gson();
//    @Autowired
//    private  ModifyResponseBodyGatewayFilterFactory factory;
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        ModifyResponseBodyGatewayFilterFactory.Config config = new ModifyResponseBodyGatewayFilterFactory.Config();
//        config.setInClass(String.class);
//        config.setOutClass(String.class);
//        config.setRewriteFunction(new RewriteFunction() {
//            @Override
//            public Object apply(Object o, Object o2) {
//                ServerWebExchange serverWebExchange = (ServerWebExchange) o;
//                String oldBody = (String) o2;
////                if (exchange.getRequest().getURI().getRawPath().contains("modifybody")) {
//                    Map map = gson.fromJson(oldBody, Map.class);
//                    map.put("hello", "new body insert!!");
//                    return Mono.just(gson.toJson(map));
////                }
////                return Mono.just(oldBody);
//            }
//        });
//        return factory.apply(config).filter(exchange, chain);
//    }
//
////    @Bean
////    public RouteLocator routes(RouteLocatorBuilder builder,ModifyResponseBodyFilter modifyResponseBodyFilter) {
////        return builder.routes()
////                .route("im-auth-route", r -> r.path("/im-auth/**")
////                        .filters(f -> f.filter(modifyResponseBodyFilter))
////                        .uri("lb://imAuth"))
////                .build();
////    }
//}
