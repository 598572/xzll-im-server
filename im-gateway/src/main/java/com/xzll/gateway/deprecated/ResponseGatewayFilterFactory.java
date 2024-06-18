//package com.xzll.gateway.config;
//
//import cn.hutool.core.collection.CollUtil;
//import cn.hutool.core.util.StrUtil;
//import com.alibaba.fastjson.JSONObject;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.cloud.gateway.filter.GatewayFilter;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
//import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
//import org.springframework.core.Ordered;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.core.io.buffer.DataBufferUtils;
//import org.springframework.core.io.buffer.DefaultDataBufferFactory;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.util.List;
//
///**
// * @Author: hzz
// * @Date: 2024/6/16 14:20:20
// * @Description:
// */
//@Slf4j
//@Component
//public class ResponseGatewayFilterFactory  extends AbstractGatewayFilterFactory<ResponseGatewayFilterFactory.Config> {
//
//    @Autowired
//    private RewriteResponseGatewayFilter rewriteResponseGatewayFilter;
//    public ResponseGatewayFilterFactory() {
//        super(Config.class);
//    }
//
//    @Data
//    @AllArgsConstructor
//    @NoArgsConstructor
//    public static class Config {
//
//        // 不需要自定义的接口
//        List<String> pathExclude;
//
//    }
//
//    @Override
//    public GatewayFilter apply(Config config) {
//        RewriteResponseGatewayFilter rewriteResponseGatewayFilter = new RewriteResponseGatewayFilter(config);
//        return rewriteResponseGatewayFilter;
//    }
//
////
////    public class RewriteResponseGatewayFilter implements GatewayFilter, Ordered {
////
////        private Config config;
////
////        public RewriteResponseGatewayFilter(Config config) {
////            this.config = config;
////        }
////
////        @Override
////        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
////            // 不需要自定义的接口，直接返回
////            log.info("pathExclude:{}", config.pathExclude);
////            if (CollUtil.isNotEmpty(config.pathExclude)) {
////                long count = config.pathExclude.stream()
////                        .filter(uri -> StrUtil.contains(exchange.getRequest().getPath().toString(), uri))
////                        .count();
////                if (count > 0) {
////                    return chain.filter(exchange);
////                }
////            }
//////            String appId = exchange.getRequest().getHeaders().getFirst("X-APPID");
//////            if (StrUtil.isBlank(appId)) {
//////                return buildResponse(exchange, HttpStatus.UNAUTHORIZED.value(), "appId不能为空");
//////            }
////            ServerHttpResponse originalResponse = exchange.getResponse();
////            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
////            try {
////                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
////                    @Override
////                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
////                        if (body instanceof Flux) {
////                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
////                            return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
////                                log.info("哈嘿哈哈哈哈哈");
////                                byte[] newContent = new byte[0];
////                                try {
////                                    DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
////                                    DataBuffer join = dataBufferFactory.join(dataBuffers);
////                                    byte[] content = new byte[join.readableByteCount()];
////                                    join.read(content);
////                                    DataBufferUtils.release(join);
////                                    // 获取响应数据
////                                    String responseStr = new String(content, "UTF-8");
////                                    // 修改响应数据
////                                    JSONObject jsonObject = new JSONObject();
////                                    jsonObject.put("code", HttpStatus.UNAUTHORIZED.value());
////                                    jsonObject.put("message", "请求成功");
////                                    jsonObject.put("data", responseStr);
////                                    String message = jsonObject.toJSONString();
////                                    newContent = message.getBytes("UTF-8");
////                                    originalResponse.getHeaders().setContentLength(newContent.length);
////                                }
////                                catch (Exception e) {
//////                                    log.error("appId:{}, responseStr exchange error:{}", appId, e);
////                                    throw new RuntimeException(e);
////                                }
////                                return bufferFactory.wrap(newContent);
////                            }));
////                        }
////                        return super.writeWith(body);
////                    }
////
////                    @Override
////                    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
////                        return writeWith(Flux.from(body).flatMapSequential(p -> p));
////                    }
////                };
////                return chain.filter(exchange.mutate().response(decoratedResponse).build());
////            }
////            catch (Exception e) {
////                log.error("RewriteResponse error:{}", e);
////                return Mono.error(new Exception("RewriteResponse fail", e));
////            }
////        }
////
////        @Override
////        public int getOrder() {
////            return Ordered.HIGHEST_PRECEDENCE;
////        }
////
////    }
//
//    @SneakyThrows
//    public static Mono<Void> buildResponse(ServerWebExchange exchange, int code, String message) {
//        ServerHttpResponse response = exchange.getResponse();
//        response.setStatusCode(HttpStatus.OK);
//        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
//        JSONObject jsonObject = new JSONObject();
//        jsonObject.put("code", code);
//        jsonObject.put("message", message);
//        jsonObject.put("data", "");
//        byte[] bytes = jsonObject.toJSONString().getBytes("UTF-8");
//        DataBuffer bodyDataBuffer = response.bufferFactory().wrap(bytes);
//        return response.writeWith(Mono.just(bodyDataBuffer));
//    }
//}
