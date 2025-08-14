package com.xzll.gateway.component;

import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:51:43
 * @Description: 无权限处理
 */
@Component
public class RestfulAccessDeniedHandler implements ServerAccessDeniedHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        // 获取请求信息
        String requestPath = exchange.getRequest().getURI().getPath();
        String requestMethod = exchange.getRequest().getMethod().name();
        
        // 构建详细的错误信息
        String errorMessage = String.format("权限不足，无法访问接口 [%s] %s", requestMethod, requestPath);
        
        String body = JSONUtil.toJsonStr(WebBaseResponse.returnResultError(
                AnswerCode.FORBIDDEN.getCode(), 
                errorMessage
        ));
        
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
