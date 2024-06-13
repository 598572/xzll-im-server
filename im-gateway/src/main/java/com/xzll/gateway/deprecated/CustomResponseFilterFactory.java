import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;

//package com.xzll.gateway.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import org.springframework.cloud.gateway.filter.GatewayFilter;
//import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
//import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
//import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
//import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
//import org.springframework.context.annotation.Bean;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.codec.HttpMessageReader;
//import org.springframework.http.codec.json.Jackson2JsonDecoder;
//import org.springframework.http.codec.json.Jackson2JsonEncoder;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//import java.util.List;
//import java.util.Set;
//
///**
// * @Author: hzz
// * @Date: 2024/6/12 14:28:33
// * @Description:
// */
//@Component
//public class CustomResponseFilterFactory extends ModifyResponseBodyGatewayFilterFactory {
//
//
//
//    private final ObjectMapper objectMapper;
//
//    public CustomResponseFilterFactory(List<HttpMessageReader<?>> messageReaders, Set<MessageBodyDecoder> messageBodyDecoders, Set<MessageBodyEncoder> messageBodyEncoders, ObjectMapper objectMapper) {
//        super(messageReaders, messageBodyDecoders, messageBodyEncoders);
//        this.objectMapper = objectMapper;
//    }
//
//    @Override
//    public GatewayFilter apply(Config config) {
//        return new ModifyResponseGatewayFilter(this.getConfig());
//    }
//
//    private Config getConfig() {
//        Config cf = new Config();
//        cf.setInClass(String.class);
//        cf.setOutClass(String.class);
//        cf.setRewriteFunction(getRewriteFunction());
//        return cf;
//    }
//
//    /**
//     * 重写 Response 返回体
//     *
//     * 如果 HTTP status 为 429, 则修改
//     *
//     * @return 重写方法
//     */
//    private RewriteFunction<String,String> getRewriteFunction() {
//        return (exchange, resp) -> {
//////            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
////                // 设置 HTTP 状态为 500
////                exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
////                String data = "{\"code\":" + 11429 + ",\"msg\": \"" + "request limit" + "\"}";
//////                return Mono.just(data.getBytes());
//////            }
//            try {
//                System.out.println("我去真行啊");
//                ObjectNode responseBody = (ObjectNode) objectMapper.readTree(resp);
//                responseBody.put("customField", "customValue");
//                return Mono.just(objectMapper.writeValueAsString(responseBody));
//            } catch (Exception e) {
//                return Mono.error(e);
//            }
////            return Mono.just(resp);
//        };
//    }
//
//}
