//package com.xzll.gateway.filter;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.xzll.common.util.NetUtils;
//import com.xzll.common.util.TraceIdUtil;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.core.io.buffer.DataBufferUtils;
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
//@Order(value = Ordered.HIGHEST_PRECEDENCE)
//public class RequestLogFilter implements WebFilter {
//
//    private static final String START_TIME = "startTime";
//    private static final String PARAM_SEPARATOR = "&";
//    public static final String STR = "\n";
//    public static final String LINK = "=";
//
//    private final ObjectMapper objectMapper;
//
//    private final List<HttpMessageReader<?>> messageReaders = getMessageReaders();
//
//    private List<HttpMessageReader<?>> getMessageReaders() {
//        return HandlerStrategies.withDefaults().messageReaders();
//    }
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
//        TraceIdUtil.setTraceId();
//        log.info("================ 网关请求开始  ================");
//
//        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());
//        exchange.getAttributes().put(TraceIdUtil.TRACE_ID, TraceIdUtil.getTraceIdByLocal());
//
//        try {
//            StringBuilder sb = new StringBuilder();
//            //get类型参数
//            exchange.getRequest().getQueryParams().forEach((key, values) ->
//                    values.forEach(value -> sb.append(key).append(LINK).append(value).append(STR))
//            );
//            //form data类型参数（如文件上传）
//            exchange.getFormData().doOnNext(formData -> {
//                if (!formData.isEmpty()) {
//                    formData.forEach((key, values) -> values.forEach(value -> sb.append(key).append(LINK).append(value).append(STR)));
//                }
//            });
//            AtomicReference<ServerHttpRequest> serverHttpRequestAtomicReference = null;
//            if (HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
//                return logRequestBody(exchange, chain);
//            }
//        } catch (Exception e) {
//            log.error("【网关】日志记录过滤器异常:", e);
//        } finally {
//            //若在此移除链路id的话 controller将获取不到链路信息，由于是流式处理 所以需要在流式处理的最后一步即：flux 或者 mono的 doFinally中移除链路信息。
//        }
//        return chain.filter(exchange);
//    }
//
//    private Mono<Void> logRequestBody(ServerWebExchange exchange, WebFilterChain chain) {
//        //参数打印
//        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
//        String requestUri = exchange.getRequest().getURI().getRawPath();
//        String ipAddress = NetUtils.getIpAddress(exchange);
//        String requestMethod = exchange.getRequest().getMethodValue();
//        return DataBufferUtils.join(exchange.getRequest().getBody())
//                .flatMap(dataBuffer -> {
//                    DataBufferUtils.retain(dataBuffer);
//
//                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
//                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
//                        @Override
//                        public Flux<DataBuffer> getBody() {
//                            return cachedFlux;
//                        }
//                    };
//
//                    return ServerRequest
//                            // must construct a new exchange instance, same as below
//                            .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
//                            .bodyToMono(String.class)
//                            .flatMap(body -> {
//                                // do what ever you want with this body string, I logged it.
//                                log.info("网关请求日志_uri:{} ip:{} method:{} contentType:{} requestParam:{}", requestUri, ipAddress, requestMethod, contentType, body);
//                                // by putting reutrn statement in here, urge Java to execute the above statements
//                                // put this final return statement outside then you'll find out that above statements inside here are not executed.
//                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
//                            });
//                });
//    }
//
////    private byte[] printResponseLog(DataBuffer dataBuffer, ServerWebExchange exchange, ServerHttpResponse originalResponse) {
////        byte[] content = new byte[dataBuffer.readableByteCount()];
////        dataBuffer.read(content);
////        String responseBody = new String(content, StandardCharsets.UTF_8);
////        Long startTime = exchange.getAttribute(START_TIME) == null ? System.currentTimeMillis() : exchange.getAttribute(START_TIME);
////        log.info("网关响应日志_uri:{},method:{},status:{},execTime:{} ms, responseData:{}", exchange.getRequest().getURI(), exchange.getRequest().getMethod(), (originalResponse.getStatusCode() == null ? "" : originalResponse.getStatusCode().value()), (System.currentTimeMillis() - startTime), responseBody);
////        log.info("================  网关响应完成  =================");
////        return content;
////    }
//
//
////    private Mono<String> readBody(ServerWebExchange exchange) {
//////         DataBufferUtils.join(exchange.getRequest().getBody())
//////                .flatMap(dataBuffer -> {
//////                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
//////                    dataBuffer.read(bytes);
//////                    DataBufferUtils.release(dataBuffer);
//////                    String s = new String(bytes, StandardCharsets.UTF_8);
//////                    log.info("请求内网是:{}",s);
//////                    return Mono.just(s);
//////                });
//////        DataBufferUtils.join(exchange.getRequest().getBody())
//////                .flatMap(dataBuffer -> {
//////                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
//////                    dataBuffer.read(bytes);
////////                    DataBufferUtils.release(dataBuffer);
//////                    String s = new String(bytes, StandardCharsets.UTF_8);
//////                    log.info("请求内网是:{}",s);
//////
//////
//////                });
//////        ServerHttpRequest mutatedRequest = null;
////        AtomicReference<ServerWebExchange> mutatedExchange = null;
////        DataBufferUtils.join(exchange.getRequest().getBody())
////                .flatMap(dataBuffer -> {
////                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
////                    dataBuffer.read(bytes);
////                    DataBufferUtils.release(dataBuffer);
////                    String bodyString = new String(bytes, StandardCharsets.UTF_8);
////
////                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
////
////                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
////                        @Override
////                        public Flux<DataBuffer> getBody() {
////                            return cachedFlux;
////                        }
////                    };
////
////                    // 打印请求体内容
////                    log.info("Request Body: " + bodyString);
////
////                    // 创建新的请求以包含已读取的 body
////                    mutatedExchange.set(exchange.mutate().request(mutatedRequest).build());
////
////                    // 继续过滤链
////                    return Mono.just(bodyString);
////                });
////
////        return Mono.empty();
////    }
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
//}
