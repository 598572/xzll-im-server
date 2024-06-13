package com.xzll.gateway.filter;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.xzll.common.util.NetUtils;
import com.xzll.common.util.TraceIdUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;

import static com.xzll.common.constant.ImConstant.START_TIME;


/**
 * @Author: hzz
 * @Date: 2023/2/25 16:48:03
 * @Description: 打印网关日志并且设置traceId，此过滤器 实现WebFilter，即：对所有的请求统统拦截，并且优先级最高，请求时最先执行
 */
@Slf4j
@Component
@AllArgsConstructor
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class RequestLogRecordFilter implements WebFilter {

    public static final String LINK = "=";

    private final List<HttpMessageReader<?>> messageReaders = getMessageReaders();

    private List<HttpMessageReader<?>> getMessageReaders() {
        return HandlerStrategies.withDefaults().messageReaders();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        TraceIdUtil.setTraceId();
        log.info("================ 网关请求开始  ================");

        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());
        exchange.getAttributes().put(TraceIdUtil.TRACE_ID, TraceIdUtil.getTraceIdByLocal());
        try {
            StringBuilder sb = new StringBuilder();
            //get类型参数
            exchange.getRequest().getQueryParams().forEach((key, values) ->
                    values.forEach(value -> sb.append(key).append(LINK).append(value))
            );
            //form data类型参数（如文件上传）
            exchange.getFormData().doOnNext(formData -> {
                if (!formData.isEmpty()) {
                    formData.forEach((key, values) -> values.forEach(value -> sb.append(key).append(LINK).append(value)));
                }
            });
            MediaType contentType = exchange.getRequest().getHeaders().getContentType();
            if (StringUtils.isNotBlank(sb)) {
                String requestUri = exchange.getRequest().getURI().getRawPath();
                String ipAddress = NetUtils.getIpAddress(exchange);
                String requestMethod = exchange.getRequest().getMethodValue();
                log.info("网关请求日志_uri:{} ip:{} method:{} contentType:{} requestData:{}", requestUri, ipAddress, requestMethod, contentType, sb);
            }
            if (HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
                return logRequestBody(exchange, chain);
            }
        } catch (Exception e) {
            log.error("【网关】请求日志记录过滤器异常:", e);
        } finally {
            //若在此移除链路id的话 controller将获取不到链路信息，由于是流式处理 所以需要在流式处理的最后一步即：flux 或者 mono的 doFinally中移除链路信息。
        }
        return chain.filter(exchange);
    }

    /**
     * 记录请求日志 ，来源： https://github.com/spring-cloud/spring-cloud-gateway/issues/747
     *
     * @param exchange
     * @param chain
     * @return
     */
    private Mono<Void> logRequestBody(ServerWebExchange exchange, WebFilterChain chain) {
        //参数打印
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        String requestUri = exchange.getRequest().getURI().getRawPath();
        String ipAddress = NetUtils.getIpAddress(exchange);
        String requestMethod = exchange.getRequest().getMethodValue();
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    DataBufferUtils.retain(dataBuffer);

                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };
                    return ServerRequest
                            // must construct a new exchange instance, same as below
                            .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
                            .bodyToMono(String.class)
                            .flatMap(body -> {
                                // do what ever you want with this body string, I logged it.
                                if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                                    JSON parse = JSONUtil.parse(body);
                                    body = JSONUtil.toJsonStr(parse);
                                }
                                log.info("网关请求日志_uri:{} ,ip:{} ,method:{} ,contentType:{} ,requestData:{}", requestUri, ipAddress, requestMethod, contentType, body);
                                // by putting reutrn statement in here, urge Java to execute the above statements
                                // put this final return statement outside then you'll find out that above statements inside here are not executed.
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            });
                });
    }
}
