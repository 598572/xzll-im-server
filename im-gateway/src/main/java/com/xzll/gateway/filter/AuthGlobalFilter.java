package com.xzll.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.nimbusds.jose.JWSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:42:24
 * @Description: 认证过滤器
 */
@Component
@Order(10)
@Slf4j
public class AuthGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //所有白名单接口已经去掉header中的Authorization属性
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StrUtil.isEmpty(token)) {
            return chain.filter(exchange);
        }
        try {
            //从token中解析用户信息并设置到Header中去
            String realToken = token.replace("Bearer ", "");
            JWSObject jwsObject = JWSObject.parse(realToken);
            String userStr = jwsObject.getPayload().toString();
            log.info("AuthGlobalFilter.filter() user:{}", userStr);
            
            JSONObject userJson = JSON.parseObject(userStr);
            String userId = null;
            if (userJson != null && userJson.containsKey("id")) {
                userId = userJson.getString("id");
            }
            
            ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                    .header("user", userStr);
            
            // 如果成功提取到userId，添加到X-User-Id头中
            if (StrUtil.isNotEmpty(userId)) {
                requestBuilder.header("X-User-Id", userId);
                log.debug("从JWT中提取userId并添加到请求头: {}", userId);
            } else {
                log.warn("无法从JWT中提取userId，payload: {}", userStr);
            }
            
            ServerHttpRequest request = requestBuilder.build();
            exchange = exchange.mutate().request(request).build();
        } catch (Exception e) {
            log.error("网关认证过滤器异常e:",e);
        }
        return chain.filter(exchange);
    }

}
