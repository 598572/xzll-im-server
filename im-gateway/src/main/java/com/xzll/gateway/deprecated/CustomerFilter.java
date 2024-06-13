//package com.xzll.gateway.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
//import org.springframework.cloud.gateway.support.BodyInserterContext;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseCookie;
//
//import org.springframework.http.client.reactive.ClientHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.reactive.function.BodyInserter;
//import org.springframework.web.reactive.function.BodyInserters;
//import org.springframework.web.reactive.function.client.ExchangeStrategies;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//import java.net.URI;
//import java.util.Objects;
//import java.util.concurrent.atomic.AtomicBoolean;
//
///**
// * @Author: hzz
// * @Date: 2024/6/12 14:11:56
// * @Description:
// */
//@Component
//@Slf4j
//public class CustomerFilter  implements GlobalFilter{
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        ServerHttpRequest request = exchange.getRequest();
//        URI uri = request.getURI();
//        String url = uri.getPath();
//
//        HttpStatus statusCode = exchange.getResponse().getStatusCode();
//        if(Objects.equals(statusCode, HttpStatus.BAD_REQUEST) || Objects.equals(statusCode, HttpStatus.TOO_MANY_REQUESTS)){
//            // 如果是特殊的请求，已处理响应内容，这里不再处理
//            return chain.filter(exchange);
//        }
//
//        // 根据具体业务内容，修改响应体
//        return modifyResponseBody(exchange, chain);
//    }
//
//    /**
//     * 修改响应体
//     * @param exchange
//     * @param chain
//     * @return
//     */
//    private Mono<Void> modifyResponseBody(ServerWebExchange exchange, GatewayFilterChain chain)  {
//        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
//            @Override
//            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
//                MediaType originalResponseContentType = (MediaType)exchange.getAttribute("original_response_content_type");
//                HttpHeaders httpHeaders = new HttpHeaders();
//                AtomicBoolean isHttpCodeOK = new AtomicBoolean(true);
//                httpHeaders.setContentType(originalResponseContentType);
//                ResponseAdapter responseAdapter = new ResponseAdapter(body, httpHeaders);
//                HttpStatus statusCode = this.getStatusCode();
//
//                // 修改后的响应体
//                Mono modifiedBody = getModifiedBody(statusCode, isHttpCodeOK, responseAdapter, exchange);
//
//                // 业务上的开关，表示是否开启加密，如果开启，就需要修改响应体，将响应体数据加密。开关从上下文获取。这里只关心是一个boolean值即可。
//                Boolean flag = true;
//
//                BodyInserter bodyInserter;
//                if(!flag) {
//                    // 不需要修改响应数据
//                    bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
//                }else {
//                    // 需要修改响应数据
//                    // 这里最后采用了 ByteArrayResource 类去处理
//                    bodyInserter = BodyInserters.fromPublisher(modifiedBody, ByteArrayResource.class);
//                }
//                CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, httpHeaders);
//                return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
//                    Flux<DataBuffer> messageBody = outputMessage.getBody();
//                    ServerHttpResponse httpResponse = this.getDelegate();
//                    HttpHeaders headers = httpResponse.getHeaders();
//                    if (!headers.containsKey("Transfer-Encoding")) {
//                        messageBody = messageBody.doOnNext((data) -> {
//                            headers.setContentLength((long)data.readableByteCount());
//                        });
//                    }
//                    if(!isHttpCodeOK.get()){
//                        // 业务处理不是200，说明有异常，设置httpCode状态码是500
//                        httpResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
//                    }
//
//                    return httpResponse.writeWith(messageBody);
//                }));
//            }
//
//            @Override
//            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
//                return this.writeWith(Flux.from(body).flatMapSequential((p) -> {
//                    return p;
//                }));
//            }
//        };
//        return chain.filter(exchange.mutate().response(responseDecorator).build());
//    }
//
//    public class ResponseAdapter implements ClientHttpResponse {
//        private final Flux<DataBuffer> flux;
//        private final HttpHeaders headers;
//
//        public ResponseAdapter(Publisher<? extends DataBuffer> body, HttpHeaders headers) {
//            this.headers = headers;
//            if (body instanceof Flux) {
//                this.flux = (Flux)body;
//            } else {
//                this.flux = ((Mono)body).flux();
//            }
//
//        }
//
//        @Override
//        public Flux<DataBuffer> getBody() {
//            return this.flux;
//        }
//
//        @Override
//        public HttpHeaders getHeaders() {
//            return this.headers;
//        }
//
//        @Override
//        public HttpStatus getStatusCode() {
//            return null;
//        }
//
//        @Override
//        public int getRawStatusCode() {
//            return 0;
//        }
//
//        public MultiValueMap<String, ResponseCookie> getCookies() {
//            return null;
//        }
//    }
//
//
//
//    private Mono getModifiedBody(HttpStatus httpStatus, AtomicBoolean isHttpCodeOK, ResponseAdapter responseAdapter, ServerWebExchange exchange){
////        switch (httpStatus){
//            // 业务上需要特殊处理的状态码
////            case BAD_REQUEST:
////            case METHOD_NOT_ALLOWED:
////                isHttpCodeOK.set(false);
////                return getMono(HttpCode.BAD_REQUEST, exchange);
////            case INTERNAL_SERVER_ERROR:
////                isHttpCodeOK.set(false);
////                return getMono(HttpCode.SERVER_ERROR, exchange);
////            default:
//                // 主要处理流程
//                return getNormalBody(isHttpCodeOK, responseAdapter, exchange);
////        }
//    }
//
//
//
//    private Mono getNormalBody(AtomicBoolean isHttpCodeOK, ResponseAdapter responseAdapter, ServerWebExchange exchange){
//        DefaultClientResponse clientResponse = new DefaultClientResponse(responseAdapter, ExchangeStrategies.withDefaults());
//        return clientResponse.bodyToMono(String.class).flatMap((originalBody) -> {
//            // 业务上的开关，表示是否开启加密，如果开启，就需要修改响应体，将响应体数据加密。开关从上下文获取。这里只关心是一个boolean值即可。
//            Boolean flag;
//
//
//            ObjectMapper objectMapper = new ObjectMapper();
//            try {
//                R r = objectMapper.readValue(originalBody, R.class);
//                /**
//                 * 异常处理流程
//                 */
//                if(!r.getCode().equals(HttpCode.SUCCESS.getCode())){
//                    // 业务处理不是200，说明有异常，直接返回对应错误
//                    isHttpCodeOK.set(false);
//                    ErrorR errorR = new ErrorR()
//                            .setCode(r.getCode())
//                            .setMsg(r.getMsg());
//                    String json = objectMapper.writeValueAsString(errorR);
//                    log.info("json = {}", json);
//                    if(!flag) {
//                        // 不需要加密，则不修改响应体
//                        return Mono.just(json);
//                    }else {
//                        // 对返回数据进行加密 EncryptionUtil.encrypt(json, key)
//                        // 具体加密逻辑不再阐述，这里可以理解成string 转成 byte[] 处理
//                        byte[] encrypt = EncryptionUtil.encrypt("{}", key);
//                        ByteArrayResource byteArrayResource = new ByteArrayResource(encrypt);
//                        // 修改响应体，使用 byteArrayResource 封装
//                        return Mono.just(byteArrayResource);
//                    }
//                }
//                // 业务处理是200，截取data内容
//                Object data = r.getData();
//                if(null == data){
//                    // 返回数据如果为空，则返回空对象
//                    if(!flag) {
//                        // 不需要加密，则不修改响应体
//                        return Mono.just("{}");
//                    }else {
//                        // 对返回数据进行加密 EncryptionUtil.encrypt(json, key)
//                        // 具体加密逻辑不再阐述，这里可以理解成string 转成 byte[] 处理
//                        byte[] encrypt = EncryptionUtil.encrypt("{}", key);
//                        ByteArrayResource byteArrayResource = new ByteArrayResource(encrypt);
//                        return Mono.just(byteArrayResource);
//                    }
//                }
//
//
//                /**
//                 * 主要处理流程
//                 */
//                String json = objectMapper.writeValueAsString(data);
//
//                if(!flag) {
//                    // 不需要加密，则不修改响应体
//                    return Mono.just(json);
//                }else {
//                    // 对返回数据进行加密 EncryptionUtil.encrypt(json, key)
//                    // 具体加密逻辑不再阐述，这里可以理解成string 转成 byte[] 处理
//                    byte[] encrypt = EncryptionUtil.encrypt("{}", key);
//                    ByteArrayResource byteArrayResource = new ByteArrayResource(encrypt);
//
//                    // 修改响应体，使用 byteArrayResource 封装
//                    return Mono.just(byteArrayResource);
//                }
//            }catch (Exception e){
//                e.printStackTrace();
//                log.error("convert originalBody error: " + e);
//                return Mono.just(originalBody);
//            }
//        });
//    }
//
//
//
//    private Mono getMono(HttpCode code, ServerWebExchange exchange){
//        ObjectMapper objectMapper = new ObjectMapper();
//        ErrorR errorR = new ErrorR()
//                .setCode(code.getCode())
//                .setMsg(code.getValue());
//
//        try {
//            String json = objectMapper.writeValueAsString(errorR);
//            log.info("json = {}", json);
//            // 开关从上下文获取
//            if(!flag) {
//                // 不需要加密，则不修改响应体
//                return Mono.just(json);
//            }else {
//                // 对返回数据进行加密 EncryptionUtil.encrypt(json, key)，这里不用管方法具体逻辑， 可以当做 string 转成 byte[]
//                byte[] encrypt = EncryptionUtil.encrypt(json, key);
//                ByteArrayResource byteArrayResource = new ByteArrayResource(encrypt);
//                // 修改响应体，使用 byteArrayResource 封装
//                return Mono.just(byteArrayResource);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.error("get mono error: " + e);
//            return Mono.just("\"code\": 500, \"msg\":\"error\"");
//        }
//    }
//
//}
