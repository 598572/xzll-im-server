//package com.xzll.gateway.filter;
//
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import com.google.common.base.Joiner;
//import com.google.common.base.Throwables;
//import com.google.common.collect.Lists;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.reactivestreams.Publisher;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.context.annotation.Bean;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.core.io.buffer.DataBufferUtils;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.nio.charset.Charset;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.stream.Collectors;
//
//import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;
//
///**
// * @Author: hzz
// * @Date: 2024/6/11 17:06:43
// * @Description:
// */
//@Component
//@Order(value = Ordered.HIGHEST_PRECEDENCE)
//@Slf4j
//public class AddImServerAddress implements GlobalFilter {
//
//    private static Joiner joiner = Joiner.on("");
//
//
//    private static final String LOGIN = "/oauth/token";
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        String requestPath = exchange.getRequest().getPath().toString();
//
//        ServerHttpResponse originalResponse = exchange.getResponse();
//        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
//        if (LOGIN.equals(requestPath)) {
//            ServerHttpResponseDecorator response = new ServerHttpResponseDecorator(originalResponse) {
//                @Override
//                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
//                    if (getStatusCode().equals(HttpStatus.OK) && body instanceof Flux) {
//                        // 获取ContentType，判断是否返回JSON格式数据
//                        String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
////                        if (StringUtils.isNotBlank(originalResponseContentType) && originalResponseContentType.contains("application/json")) {
//                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
//                            return super.writeWith(fluxBody.buffer().map(dataBuffers -> {//解决返回体分段传输
//                                List<String> list = Lists.newArrayList();
//                                dataBuffers.forEach(dataBuffer -> {
//                                    try {
//                                        byte[] content = new byte[dataBuffer.readableByteCount()];
//                                        dataBuffer.read(content);
//                                        DataBufferUtils.release(dataBuffer);
//                                        list.add(new String(content, "utf-8"));
//                                    } catch (Exception e) {
//                                        log.info("动态加载API加密规则失败，失败原因：{}", Throwables.getStackTraceAsString(e));
//                                    }
//                                });
//                                String responseData = joiner.join(list);
//                                log.info("============响应数据为:{}",responseData);
//                                // 二次处理（加密/过滤等）如果不需要做二次处理可直接跳过下行
////                                responseData = beforeBodyWriteInternal(responseData, exchange.getRequest());
//                                byte[] uppedContent = new String(responseData.getBytes(), Charset.forName("UTF-8")).getBytes();
//                                originalResponse.getHeaders().setContentLength(uppedContent.length);
//                                return bufferFactory.wrap(uppedContent);
//                            }));
////                        }
//                    }
//                    return super.writeWith(body);
//                }
//
//                @Override
//                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
//                    return writeWith(Flux.from(body).flatMapSequential(p -> p));
//                }
//            };
//            return chain.filter(exchange.mutate().response(response).build());
//        } else {
//            return chain.filter(exchange);
//        }
//        String requestPath = exchange.getRequest().getPath().toString();
//        // 先执行请求，然后再修改响应
////        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
////            if (LOGIN.equals(requestPath)) {
////                ServerHttpResponse response = exchange.getResponse();
////
////                // 获取现有响应体并修改
////                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
////                // 示例：追加属性到响应体（假设原始响应体是JSON）
////                byte[] modifiedResponse = "{\"message\":\"Original Response\",\"newProperty\":\"New Value\"}".getBytes(StandardCharsets.UTF_8);
////                response.writeWith(Mono.just(response.bufferFactory().wrap(modifiedResponse)));
////            }
////        }));
////
////        ServerHttpResponse originalResponse = exchange.getResponse();
////        ServerHttpRequest request = exchange.getRequest();
////        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
////            @Override
////            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
////                //修改header
////                HttpHeaders httpHeaders = originalResponse.getHeaders();
////                httpHeaders.add("xxxxxx","aaaaaa");
////                //输出返回结果
////                // 输出响应体内容
////                Flux<DataBuffer> flux = Flux.from(body);
////                return super.writeWith(flux.buffer().map(dataBuffers -> {
////                    DataBufferUtils.release(dataBuffers);
////                    // 重写响应体内容
////                    return bufferFactory.wrap("Response Body: " + dataBuffers.size());
////                }));
////            }
////
////        };
////        return chain.filter(exchange.mutate().response(decoratedResponse).build());
////
////
////
////        // 先执行请求，然后再修改响应
////        return chain.filter(exchange).then(Mono.defer(() -> {
////            if (LOGIN.equals(requestPath)) {
////                ServerHttpResponse response = exchange.getResponse();
////                response.writeWith(x->x.onSubscribe(()->{})
////                        .flatMap(dataBuffer -> {
////                            byte[] content = new byte[dataBuffer.readableByteCount()];
////                            dataBuffer.read(content);
////                            DataBufferUtils.release(dataBuffer);
////
////                            String originalResponseBody = new String(content, StandardCharsets.UTF_8);
////
////                            try {
////                                // 解析现有响应体
////                                JsonNode jsonNode = objectMapper.readTree(originalResponseBody);
////                                // 将自定义字段 address 添加到 data 对象中
////                                ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
////
////                                // 将修改后的 JSON 写回响应体
////                                byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
////                                DataBuffer buffer = response.bufferFactory().wrap(modifiedResponseBody);
////
////                                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
////                                response.getHeaders().setContentLength(modifiedResponseBody.length);
////
////                                return response.writeWith(Mono.just(buffer));
////                            } catch (Exception e) {
////                                log.error("Error processing response body", e);
////                                return Mono.error(e);
////                            }
////                        }));
////                response.writeWith();
//        // 获取现有响应体并修改
////                return response.writeWith(
////                        response.getBody().next().flatMap(buffer -> {
////                            String originalResponseBody = buffer.toString(StandardCharsets.UTF_8);
////                            try {
////                                // 解析现有响应体
////                                JsonNode jsonNode = objectMapper.readTree(originalResponseBody);
////                                // 将自定义字段 address 添加到 data 对象中
////                                ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
////
////                                // 将修改后的 JSON 写回响应体
////                                byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
////                                return response.writeWith(Mono.just(response.bufferFactory().wrap(modifiedResponseBody)));
////                            } catch (Exception e) {
////                                log.error("Error processing response body", e);
////                                return Mono.error(e);
////                            }
////                        })
////                );
////            } else {
////                return Mono.empty();
////            }
////        }));
////    }
//
//
//    // UserRouter.java
//
////    @Bean
////    public RouterFunction<ServerResponse> demo2RouterFunction() {
////        return route(GET("/users2/demo2"), request -> ok().bodyValue("demo"))
////                .filter(new HandlerFilterFunction<ServerResponse, ServerResponse>() {
////
////                    @Override
////                    public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
////                        return next.handle(request).doOnSuccess(new Consumer<ServerResponse>() { // 执行成功后回调
////
////                            @Override
////                            public void accept(ServerResponse serverResponse) {
////                                logger.info("[accept][执行成功]");
////                            }
////
////                        });
////                    }
////
////                });
////    }
//
//
//    private static final String LOGIN = "/oauth/token";
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        String requestPath = exchange.getRequest().getPath().toString();
//
//        if (LOGIN.equals(requestPath)) {
//            ServerHttpResponse originalResponse = exchange.getResponse();
//            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
//            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
//                @Override
//                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
//                    if (getStatusCode().equals(HttpStatus.OK) && body instanceof Flux) {
//                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
//                        Mono<? extends List<? extends DataBuffer>> monoBufferList = fluxBody.collectList();
//                        return monoBufferList.flatMap(dataBuffers -> {
//                            List<byte[]> list = dataBuffers.stream().map(dataBuffer -> {
//                                byte[] content = new byte[dataBuffer.readableByteCount()];
//                                dataBuffer.read(content);
//                                DataBufferUtils.release(dataBuffer);
//                                return content;
//                            }).collect(Collectors.toList());
//
//                            String responseBody = list.stream()
//                                    .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
//                                    .collect(Collectors.joining());
//
//                            try {
//                                JsonNode jsonNode = objectMapper.readTree(responseBody);
//                                ((ObjectNode) jsonNode.get("data")).put("address", "your-address-value");
//
//                                byte[] modifiedResponseBody = objectMapper.writeValueAsBytes(jsonNode);
//                                DataBuffer buffer = bufferFactory.wrap(modifiedResponseBody);
//
//                                originalResponse.getHeaders().setContentLength(modifiedResponseBody.length);
//                                originalResponse.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
//
//                                return super.writeWith(Mono.just(buffer));
//                            } catch (Exception e) {
//                                log.error("Error processing response body", e);
//                                return Mono.error(e);
//                            }
//                        });
//                    }
//                    return super.writeWith(body);
//                }
//
//                @Override
//                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
//                    return writeWith(Flux.from(body).flatMapSequential(p -> p));
//                }
//            };
//            return chain.filter(exchange.mutate().response(decoratedResponse).build());
//        } else {
//            return chain.filter(exchange);
//        }
//    }
//}
