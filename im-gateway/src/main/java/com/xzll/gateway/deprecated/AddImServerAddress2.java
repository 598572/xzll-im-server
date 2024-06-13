//package com.xzll.gateway.filter;
//
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import com.google.common.base.Joiner;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.cloud.gateway.route.RouteLocator;
//import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.core.io.buffer.DataBufferUtils;
//import org.springframework.core.io.buffer.DefaultDataBufferFactory;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * @Author: hzz
// * @Date: 2024/6/11 17:06:43
// * @Description:
// */
//@Component
//@Order(value = Ordered.HIGHEST_PRECEDENCE)
//@Slf4j
//public class AddImServerAddress2 implements GlobalFilter {
//
//    private static final String LOGIN_PATH = "/oauth/token";
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        ServerHttpRequest request = exchange.getRequest();
//        if (LOGIN_PATH.equals(request.getPath().toString())) {
//            return chain.filter(exchange).then(Mono.defer(() -> {
//                ServerHttpResponse response = exchange.getResponse();
//                if (response.getStatusCode() == HttpStatus.OK && response.getHeaders().getContentType().equals(MediaType.APPLICATION_JSON)) {
//                    return response.writeWith(response.getBody().flatMap(dataBuffer -> {
//                        byte[] content = new byte[dataBuffer.readableByteCount()];
//                        dataBuffer.read(content);
//                        DataBufferUtils.release(dataBuffer);
//                        String responseBody = new String(content, StandardCharsets.UTF_8);
//
//                        try {
//                            JsonNode jsonNode = objectMapper.readTree(responseBody);
//                            ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
//
//                            byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
//                            response.getHeaders().setContentLength(modifiedResponseBody.length);
//                            return response.writeWith(Mono.just(response.bufferFactory().wrap(modifiedResponseBody)));
//                        } catch (Exception e) {
//                            log.error("Error processing response body", e);
//                            return Mono.error(e);
//                        }
//                    }));
//                }
//                return Mono.empty();
//            }));
//        }
//        return chain.filter(exchange);
//    }
//
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        String requestPath = exchange.getRequest().getPath().toString();
//
//        ServerHttpResponse originalResponse = exchange.getResponse();
//        if (LOGIN_PATH.equals(requestPath)) {
//            ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(originalResponse) {
//                @Override
//                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
//                    if (getStatusCode().equals(HttpStatus.OK) && getHeaders().getContentType().equals(MediaType.APPLICATION_JSON)) {
//                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
//                        return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
//                            DataBuffer joinedBuffers = new DefaultDataBufferFactory().join(dataBuffers);
//                            byte[] content = new byte[joinedBuffers.readableByteCount()];
//                            joinedBuffers.read(content);
//                            DataBufferUtils.release(joinedBuffers);
//
//                            String responseBody = new String(content, StandardCharsets.UTF_8);
//                            try {
//                                JsonNode jsonNode = objectMapper.readTree(responseBody);
//                                ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
//
//                                byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
//                                getHeaders().setContentLength(modifiedResponseBody.length);
//                                return super.writeWith(Mono.just(bufferFactory().wrap(modifiedResponseBody)));
//                            } catch (Exception e) {
//                                log.error("Error processing response body", e);
//                                return Mono.error(e);
//                            }
//                        }));
//                    }
//                    return super.writeWith(body);
//                }
//            };
//            return chain.filter(exchange.mutate().response(responseDecorator).build());
//        } else {
//            return chain.filter(exchange);
//        }
//    }
//
//
//    private static final String LOGIN_PATH = "/oauth/token";
////    private final ObjectMapper objectMapper = new ObjectMapper();
//
////    @Override
////    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
////        String requestPath = exchange.getRequest().getPath().toString();
////
////        ServerHttpResponse originalResponse = exchange.getResponse();
////        if (LOGIN_PATH.equals(requestPath)) {
////            ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(originalResponse) {
////                @Override
////                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
////                    if (getStatusCode().equals(HttpStatus.OK) && getHeaders().getContentType().equals(MediaType.APPLICATION_JSON)) {
////                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
////                        return super.writeWith(fluxBody.collectList().flatMap(dataBuffers -> {
////                            DataBuffer joinedBuffers = new DefaultDataBufferFactory().join(dataBuffers);
////                            byte[] content = new byte[joinedBuffers.readableByteCount()];
////                            joinedBuffers.read(content);
////                            DataBufferUtils.release(joinedBuffers);
////
////                            String responseBody = new String(content, StandardCharsets.UTF_8);
////                            try {
////                                JsonNode jsonNode = objectMapper.readTree(responseBody);
////                                ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
////
////                                byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
////                                getHeaders().setContentLength(modifiedResponseBody.length);
////                                return getDelegate().writeWith(Mono.just(bufferFactory().wrap(modifiedResponseBody)));
////                            } catch (Exception e) {
////                                log.error("Error processing response body", e);
////                                return Mono.error(e);
////                            }
////                        }));
////                    }
////                    return super.writeWith(body);
////                }
////
////                @Override
////                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
////                    return writeWith(Flux.from(body).flatMapSequential(p -> p));
////                }
////            };
////            return chain.filter(exchange.mutate().response(responseDecorator).build());
////        } else {
////            return chain.filter(exchange);
////        }
////    }
//
////    @Override
////    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
////        String requestPath = exchange.getRequest().getPath().toString();
////
////        ServerHttpResponse originalResponse = exchange.getResponse();
////        if (LOGIN_PATH.equals(requestPath)) {
////            ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(originalResponse) {
////                @Override
////                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
////                    if (getStatusCode().equals(HttpStatus.OK) && getHeaders().getContentType() != null && getHeaders().getContentType().includes(MediaType.APPLICATION_JSON)) {
////                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
////                        return super.writeWith(fluxBody.collectList().flatMap(dataBuffers -> {
////                            DataBuffer joinedBuffers = new DefaultDataBufferFactory().join(dataBuffers);
////                            byte[] content = new byte[joinedBuffers.readableByteCount()];
////                            joinedBuffers.read(content);
////                            DataBufferUtils.release(joinedBuffers);
////
////                            String responseBody = new String(content, StandardCharsets.UTF_8);
////                            try {
////                                JsonNode jsonNode = objectMapper.readTree(responseBody);
////                                ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
////
////                                byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
////                                DataBuffer buffer = bufferFactory().wrap(modifiedResponseBody);
//////                                return getDelegate().writeWith(Mono.just(buffer));
////                                return getDelegate().writeWith(Mono.just(buffer));
////                            } catch (Exception e) {
////                                log.error("Error processing response body", e);
////                                return Mono.error(e);
////                            }
////                        }));
////                    }
////                    return super.writeWith(body);
////                }
////
////                @Override
////                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
////                    return writeWith(Flux.from(body).flatMapSequential(p -> p));
////                }
////            };
////            return chain.filter(exchange.mutate().response(responseDecorator).build());
////        } else {
////            return chain.filter(exchange);
////        }
////    }
//
////    @Bean
////    public RouteLocator routes(RouteLocatorBuilder builder) {
////        return builder.routes()
////                .route("rewrite_response_upper", r -> r.host("*.rewriteresponseupper.org")
////                        .filters(f -> f.prefixPath("/httpbin")
////                                .modifyResponseBody(String.class, String.class,
////                                        (exchange, s) -> Mono.just(s.toUpperCase()))).uri(uri)
////                        .build();
////    }
//
//    @Bean
//    public RouteLocator routes(RouteLocatorBuilder builder) {
//        return builder.routes()
//                .route( r -> r.path(LOGIN_PATH)
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
//                                })).uri("lb://imAuth"))
//                .build();
//    }
//}
