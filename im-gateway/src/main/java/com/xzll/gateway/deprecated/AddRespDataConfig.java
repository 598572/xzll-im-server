//package com.xzll.gateway.filter;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import org.springframework.cloud.gateway.route.RouteLocator;
//import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import reactor.core.publisher.Mono;
//
///**
// * @Author: hzz
// * @Date: 2024/6/16 11:56:14
// * @Description:
// */
//@Configuration
//public class AddRespDataConfig {
//
//    private static final String LOGIN_PATH = "/oauth/token";
//
//    @Bean
//    public RouteLocator routes(RouteLocatorBuilder builder) {
//        return builder.routes()
//                .route( r -> r.path("/im-auth/oauth/token")
//                        .filters(f -> f.modifyResponseBody(String.class, String.class,
//                                (exchange, originalBody) -> {
//                                    // 解析和修改响应体
//                                    try {
//                                        ObjectMapper objectMapper = new ObjectMapper();
//                                        JsonNode jsonNode = objectMapper.readTree(originalBody);
//                                        ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
//                                        return Mono.just(objectMapper.writeValueAsString(jsonNode));
//                                    } catch (Exception e) {
//                                        return Mono.error(e);
//                                    }
//                                })).uri("http://127.0.0.1:8096"))
//                .build();
//    }
//
//}
