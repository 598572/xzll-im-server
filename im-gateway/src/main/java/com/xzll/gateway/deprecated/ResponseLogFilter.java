//package com.xzll.gateway.filter;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.xzll.common.util.NetUtils;
//import com.xzll.common.util.TraceIdUtil;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.core.io.buffer.DataBufferUtils;
//import org.springframework.core.io.buffer.DefaultDataBufferFactory;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.MediaType;
//import org.springframework.http.codec.HttpMessageReader;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.web.reactive.function.server.HandlerStrategies;
//import org.springframework.web.reactive.function.server.ServerRequest;
//import org.springframework.web.server.ServerWebExchange;
//import org.springframework.web.server.WebFilter;
//import org.springframework.web.server.WebFilterChain;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.Objects;
//import java.util.concurrent.atomic.AtomicReference;
//
//
///**
// * @Author: hzz
// * @Date: 2023/2/25 16:48:03
// * @Description: 打印网关日志并且设置traceId，此过滤器 实现WebFilter，即：对所有的请求统统拦截，并且优先级最高，请求时最先执行，响应时最后执行
// */
//@Slf4j
//@Component
//@AllArgsConstructor
//@Order(value = Ordered.HIGHEST_PRECEDENCE+1)
//public class ResponseLogFilter implements WebFilter {
//
//    private static final String START_TIME = "startTime";
//    private static final String PARAM_SEPARATOR = "&";
//    public static final String STR = "\n";
//
//    private final ObjectMapper objectMapper;
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//
//        Mono<Void> then = null;
//        ServerHttpResponseDecorator decoratedResponse = null;
//        try {
//
//            MediaType contentType = exchange.getRequest().getHeaders().getContentType();
//            if (Objects.nonNull(contentType) && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
//                return chain.filter(exchange);
//            }
//
//            StringBuilder sb = new StringBuilder();
//            // 获取并打印查询参数
//            exchange.getRequest().getQueryParams().forEach((key, values) ->
//                    values.forEach(value -> sb.append(key).append("=").append(value).append(STR))
//            );
//
//            exchange.getFormData().doOnNext(formData -> {
//                if (!formData.isEmpty()) {
//                    formData.forEach((key, values) -> values.forEach(value -> sb.append("Form Data").append(key).append("=").append(value).append(STR)));
//                }
//            });
//
//
//            //readBody(exchange);
////            Mono<String> stringMono = DataBufferUtils.join(exchange.getRequest().getBody())
////                    .flatMap(dataBuffer -> {
////                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
////                        dataBuffer.read(bytes);
////                        DataBufferUtils.release(dataBuffer);
////                        String bodyString = new String(bytes, StandardCharsets.UTF_8);
////                        // 打印请求体内容
////                        log.info("Request Body: " + bodyString);
////                        return Mono.just(bodyString);
////                    });
//
//            exchange.getAttributes().put(START_TIME, System.currentTimeMillis());
//            exchange.getAttributes().put(TraceIdUtil.TRACE_ID, TraceIdUtil.getTraceIdByLocal());
//            AtomicReference<ServerHttpRequest> serverHttpRequestAtomicReference = null;
//            if (HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
////                logRequestBody(exchange, chain);
//                //serverHttpRequestMono.
////                ServerHttpRequest originalRequest = exchange.getRequest();
////                DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
////               DataBufferUtils.join(exchange.getRequest().getBody())
////                        .flatMap(dataBuffer -> {
////                            //DataBufferUtils.retain(dataBuffer);
////
////                            Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
////                            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
////                                @Override
////                                public Flux<DataBuffer> getBody() {
////                                    return cachedFlux;
////                                }
////                            };
////                            Mono<Void> voidMono = ServerRequest
////                                    .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
////                                    .bodyToMono(String.class)
////                                    .flatMap(body -> {
////                                        // do what ever you want with this body string, I logged it.
////                                        log.info("网关请求日志_柔柔弱弱若若：requestParam:{}", body);
////                                        // by putting reutrn statement in here, urge Java to execute the above statements
////                                        // put this final return statement outside then you'll find out that above statements inside here are not executed.
////                                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
////                                    });
////                            byte[] content = new byte[dataBuffer.readableByteCount()];
////                            return Mono.just(bufferFactory.wrap(content));
////                            return ServerRequest
////                                    // must construct a new exchange instance, same as below
////                                    .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
////                                    .bodyToMono(String.class)
////                                    .flatMap(body -> {
////                                        // do what ever you want with this body string, I logged it.
////                                        log.info("网关请求日志_柔柔弱弱若若：requestParam:{}", body);
////                                        // by putting reutrn statement in here, urge Java to execute the above statements
////                                        // put this final return statement outside then you'll find out that above statements inside here are not executed.
////                                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
////                                    });
////                        });
//            }
//
////            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().build();
////            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
//
////            stringMono.doOnNext(body -> {
////                        if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
////                            sb.append("Json Body: ").append(body).append(STR);
////                        } else if (contentType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
////                            sb.append("Form-urlencoded Body: ").append(body).append(STR);
////                        }
////                    });
////                    .then(Mono.defer(() -> {
////                MediaType contentTypeTemp = exchange.getRequest().getHeaders().getContentType();
////                if (contentTypeTemp != null && (contentTypeTemp.isCompatibleWith(MediaType.APPLICATION_JSON) || contentTypeTemp.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))) {
////                    return readBody(exchange).doOnNext(body -> {
////                        if (contentTypeTemp.isCompatibleWith(MediaType.APPLICATION_JSON)) {
////                            sb.append("Json Body: ").append(body).append(STR);
////                        } else if (contentTypeTemp.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
////                            sb.append("Form-urlencoded Body: ").append(body).append(STR);
////                        }
////                    }).then();
////                } else {
////                    return Mono.empty();
////                }
////            }));
//
////            Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
////
////            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
////                @Override
////                public Flux<DataBuffer> getBody() {
////                    return cachedFlux;
////                }
////            };
//
//
//
//            ServerHttpResponse originalResponse = exchange.getResponse();
//            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
//            decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
//                @Override
//                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
//                    //Flux 和 Mono 是Reactor库中两种不同的Publisher，它们分别表示可以发出多个或单个元素的流。可以简单理解：Flux包含多个元素，Mono只有一个元素，所以这里需要 **分别判断**
//                    if (body instanceof Flux) {
//                        Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
//                        return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
//                            //Flux多个元素，需要使用join拼接起来 否则获取到的内容将不完整。即：使用join解决返回体分段传输
//                            DataBuffer joinedBuffer = bufferFactory.join(dataBuffers);
//                            TraceIdUtil.setTraceId(exchange.getAttribute(TraceIdUtil.TRACE_ID));
//                            //使用 bufferFactory.wrap(content) 创建一个新的 DataBuffer 时(在下边包装的)，不需要再使用 DataBufferUtils.retain(joinedBuffer)，
//                            //因为已经通过 bufferFactory.wrap 创建了一个新的缓冲区实例。这样可以避免不必要的引用计数管理。所以下边代码注掉
//                            //DataBufferUtils.retain(joinedBuffer);
//
//                            byte[] content = printResponseLog(joinedBuffer, exchange, originalResponse);
//
//                            //return joinedBuffer;//这样写的话数据将响应不到前端！因为joinedBuffer 在上边 的read中 ，已经被读取！
//                            //用 bufferFactory.wrap(content) 创建一个新的 DataBuffer 包装读取到的内容，以确保内容正确传递给客户端。
//                            //【注意此处必须用 bufferFactory包装，否则数据将响应不到前端！】
//                            return bufferFactory.wrap(content);
//                        })//确保当 DataBuffer 不再需要时释放它以避免 内存泄露
//                                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)).doFinally(x -> {
//                            TraceIdUtil.cleanTraceId();
//                        });
//                    } else if (body instanceof Mono) {
//                        Mono<? extends DataBuffer> monoBody = (Mono<? extends DataBuffer>) body;
//                        return monoBody.flatMap(dataBuffer -> {
//                            TraceIdUtil.setTraceId(exchange.getAttribute(TraceIdUtil.TRACE_ID));
//
//                            //使用 bufferFactory.wrap(content) 创建一个新的 DataBuffer 时(在下边包装的)，不需要再使用 DataBufferUtils.retain(joinedBuffer)，
//                            //因为已经通过 bufferFactory.wrap 创建了一个新的缓冲区实例。这样可以避免不必要的引用计数管理。所以下边代码注掉
//                            //DataBufferUtils.retain(dataBuffer);
//
//                            byte[] content = printResponseLog(dataBuffer, exchange, originalResponse);
//
//                            //return dataBuffer;//这样写的话数据将响应不到前端！因为dataBuffer 在上边 的read中 ，已经被读取！
//                            //用 bufferFactory.wrap(content) 创建一个新的 DataBuffer 包装读取到的内容，以确保内容正确传递给客户端。
//                            //【所以此处必须用 bufferFactory包装，否则数据将响应不到前端！】
//                            return super.writeWith(Mono.just(bufferFactory.wrap(content)));
//                        }).doOnDiscard(DataBuffer.class, DataBufferUtils::release).doFinally(x -> {
//                            TraceIdUtil.cleanTraceId();
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
//        } catch (Exception e) {
//            log.error("【网关】日志记录过滤器异常:", e);
//        } finally {
//            //若在此移除链路id的话 controller将获取不到链路信息，由于是流式处理 所以需要在流式处理的最后一步即：flux 或者 mono的 doFinally中移除链路信息。
//        }
//        return chain.filter(exchange);
//    }
//
//    private byte[] printResponseLog(DataBuffer dataBuffer, ServerWebExchange exchange, ServerHttpResponse originalResponse) {
//        byte[] content = new byte[dataBuffer.readableByteCount()];
//        dataBuffer.read(content);
//        String responseBody = new String(content, StandardCharsets.UTF_8);
//        Long startTime = exchange.getAttribute(START_TIME) == null ? System.currentTimeMillis() : exchange.getAttribute(START_TIME);
//        log.info("网关响应日志_uri:{},method:{},status:{},execTime:{} ms, responseData:{}", exchange.getRequest().getURI(), exchange.getRequest().getMethod(), (originalResponse.getStatusCode() == null ? "" : originalResponse.getStatusCode().value()), (System.currentTimeMillis() - startTime), responseBody);
//        log.info("================  网关响应完成  =================");
//        return content;
//    }
//
//
//    private Mono<String> readBody(ServerWebExchange exchange) {
////         DataBufferUtils.join(exchange.getRequest().getBody())
////                .flatMap(dataBuffer -> {
////                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
////                    dataBuffer.read(bytes);
////                    DataBufferUtils.release(dataBuffer);
////                    String s = new String(bytes, StandardCharsets.UTF_8);
////                    log.info("请求内网是:{}",s);
////                    return Mono.just(s);
////                });
////        DataBufferUtils.join(exchange.getRequest().getBody())
////                .flatMap(dataBuffer -> {
////                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
////                    dataBuffer.read(bytes);
//////                    DataBufferUtils.release(dataBuffer);
////                    String s = new String(bytes, StandardCharsets.UTF_8);
////                    log.info("请求内网是:{}",s);
////
////
////                });
////        ServerHttpRequest mutatedRequest = null;
//        AtomicReference<ServerWebExchange> mutatedExchange = null;
//        DataBufferUtils.join(exchange.getRequest().getBody())
//                .flatMap(dataBuffer -> {
//                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
//                    dataBuffer.read(bytes);
//                    DataBufferUtils.release(dataBuffer);
//                    String bodyString = new String(bytes, StandardCharsets.UTF_8);
//
//                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
//
//                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
//                        @Override
//                        public Flux<DataBuffer> getBody() {
//                            return cachedFlux;
//                        }
//                    };
//
//                    // 打印请求体内容
//                    log.info("Request Body: " + bodyString);
//
//                    // 创建新的请求以包含已读取的 body
//                    mutatedExchange.set(exchange.mutate().request(mutatedRequest).build());
//
//                    // 继续过滤链
//                    return Mono.just(bodyString);
//                });
//
//        return Mono.empty();
//    }
//
////    @Override
////    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
////
////        log.info("================ 网关请求开始  ================");
////        Mono<Void> then = null;
////        try {
////            ImGatewayRequestWrapper imGatewayRequestWrapper = new ImGatewayRequestWrapper((HttpServletRequest) exchange.getRequest());
////
////            TraceIdUtil.setTraceId();
////        } catch (Exception e) {
////            log.error("网关认证过滤器异常e:", e);
////        } finally {
////            TraceIdUtil.cleanTraceId();
////        }
////        return then;
////    }
//
////    private void printTraceLogStart(ImGatewayRequestWrapper request) {
////        try {
////            String ipAddress = NetUtils.getIpAddress(request);
////            String uri = request.getRequestURI();
////            String method = request.getMethod();
////            String contentType = request.getContentType();
////            String params = "";
////            if (RequestMethod.POST.name().equals(method)) {
////                params = JSONUtil.toJsonStr(request.getBody());
////            } else if (StringUtils.isNotBlank(request.getQueryString())) {
////                params = JSONUtil.toJsonStr(request.getQueryString().split(PARAM_SEPARATOR));
////            }
////            log.info("http_request_begin uri={} ip={} method={} contentType={} requestParam={}",
////                    uri, ipAddress, method, contentType, params);
////        } catch (Exception e) {
////            log.error("打印请求入参日志异常 ", e);
////        }
////    }
//
////    private void printTraceLogEnd(ImGatewayRequestWrapper request, Long startTime) {
////        try {
////            String ipAddress = NetUtils.getIpAddress(request);
////            String uri = request.getRequestURI();
////            Long cost = System.currentTimeMillis() - startTime;
////
////            log.info("http_request_end uri={} ip={} cost={} responseBody={}",
////                    uri, ipAddress, cost, result);
////        } catch (Exception e) {
////            log.error("打印请求响应日志异常 ", e);
////        }
////    }
//
//
//    private final List<HttpMessageReader<?>> messageReaders = getMessageReaders();
//
//    private List<HttpMessageReader<?>> getMessageReaders() {
//        return HandlerStrategies.withDefaults().messageReaders();
//    }
//
//    private Mono<Void> logRequestBody(ServerWebExchange exchange, WebFilterChain chain) {
//        return DataBufferUtils.join(exchange.getRequest().getBody())
//                .flatMap(dataBuffer -> {
//                    DataBufferUtils.retain(dataBuffer);
//
//                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
//
//                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
//                        @Override
//                        public Flux<DataBuffer> getBody() {
//                            return cachedFlux;
//                        }
//                    };
////                    return Mono.just(mutatedRequest);
//                    return ServerRequest
//                            // must construct a new exchange instance, same as below
//                            .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
//                            .bodyToMono(String.class)
//                            .flatMap(body -> {
//                                // do what ever you want with this body string, I logged it.
//                                log.info("请求体是 body: {}", body);
//                                // by putting reutrn statement in here, urge Java to execute the above statements
//                                // put this final return statement outside then you'll find out that above statements inside here are not executed.
//                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
//                            });
//                });
//    }
//}
