package com.xzll.gateway.filter;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.TreeMap;

/**
 * @Author: hzz
 * @Date: 2024/6/16 13:36:23
 * @Description: 修改请求体 （暂未用到 但是以后很可能需要）
 */
@Slf4j
@Order(-5)
@Component
public class ModifyRequestBodyFilter implements GlobalFilter {
    private final Gson gson = new Gson();
    private final ModifyRequestBodyGatewayFilterFactory factory = new ModifyRequestBodyGatewayFilterFactory();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            ModifyRequestBodyGatewayFilterFactory.Config config = new ModifyRequestBodyGatewayFilterFactory.Config();
            config.setInClass(String.class);
            config.setOutClass(String.class);
            config.setRewriteFunction(new RewriteFunction() {
                @Override
                public Object apply(Object o, Object o2) {
                    String oldBody = (String) o2;
                    TreeMap<String,Object> map = gson.fromJson(oldBody, TreeMap.class);
                    //添加你想添加的字段
                    return Mono.just(gson.toJson(map));
                }
            });
            return factory.apply(config).filter(exchange, chain);
        } else {
            return chain.filter(exchange);
        }
    }

}
