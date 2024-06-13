package com.xzll.gateway.filter;

import com.xzll.common.util.TraceIdUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static com.xzll.common.constant.ImConstant.START_TIME;


/**
 * @Author: hzz
 * @Date: 2023/2/25 16:48:03
 * @Description: 响应时最后执行（保证打印到最全 最完整的响应体），其实是倒数第二执行，倒数第一是RequestLogRecordFilter。
 */
@Slf4j
@Component
@AllArgsConstructor
@Order(value = -10)
public class ResponseLogRecordFilter implements WebFilter {


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponseDecorator decoratedResponse = null;
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

                    //Flux 和 Mono 是Reactor库中两种不同的Publisher，它们分别表示可以发出多个或单个元素的流。可以简单理解：Flux包含多个元素，Mono只有一个元素，在这里我使用
                    //Flux.from使得Mono类型的流元素包装为Flux， 以便统一处理不然的话还得加个else来处理是Mono的场景
                    body = Flux.from(body);
                    if (body instanceof Flux) {
                        Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                        return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                            //Flux多个元素，需要使用join拼接起来 否则获取到的内容将不完整。即：使用join解决返回体分段传输
                            DataBuffer joinedBuffer = bufferFactory.join(dataBuffers);
                            TraceIdUtil.setTraceId(exchange.getAttribute(TraceIdUtil.TRACE_ID));
                            //使用 bufferFactory.wrap(content) 创建一个新的 DataBuffer 时(在下边包装的)，不需要再使用 DataBufferUtils.retain(joinedBuffer)，
                            //因为已经通过 bufferFactory.wrap 创建了一个新的缓冲区实例。这样可以避免不必要的引用计数管理。所以下边代码注掉
                            //DataBufferUtils.retain(joinedBuffer);

                            byte[] content = printResponseLog(joinedBuffer, exchange, originalResponse);

                            //return joinedBuffer;//这样写的话数据将响应不到前端！因为joinedBuffer 在上边 的read中 ，已经被读取！
                            //用 bufferFactory.wrap(content) 创建一个新的 DataBuffer 包装读取到的内容，以确保内容正确传递给客户端。
                            //【注意此处必须用 bufferFactory包装，否则数据将响应不到前端！】
                            return bufferFactory.wrap(content);
                        })//确保当 DataBuffer 不再需要时释放它以避免 内存泄露
                                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)).doOnError(e -> {
                            log.error("网关响应记录出现异常e:", e);
                        }).doFinally(x -> {
                            TraceIdUtil.cleanTraceId();
                        });
                    }
                    return super.writeWith(body);
                }

                @Override
                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                    return writeWith(Flux.from(body).flatMapSequential(p -> p));
                }
            };
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        } catch (Exception e) {
            log.error("【网关】响应日志记录过滤器异常:", e);
        } finally {
            //若在此移除链路id的话 controller将获取不到链路信息，由于是流式处理 所以需要在流式处理的最后一步即：flux 或者 mono的 doFinally中移除链路信息。
        }
        return chain.filter(exchange);
    }

    /**
     * 打印响应
     *
     * @param dataBuffer
     * @param exchange
     * @param originalResponse
     * @return
     */
    private byte[] printResponseLog(DataBuffer dataBuffer, ServerWebExchange exchange, ServerHttpResponse originalResponse) {
        byte[] content = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(content);
        String responseBody = new String(content, StandardCharsets.UTF_8);
        Long startTime = exchange.getAttribute(START_TIME) == null ? System.currentTimeMillis() : exchange.getAttribute(START_TIME);
        log.info("网关响应日志_uri:{} ,method:{},status:{},execTime:{} ms, responseData:{}", exchange.getRequest().getURI(), exchange.getRequest().getMethod(), (originalResponse.getStatusCode() == null ? "" : originalResponse.getStatusCode().value()), (System.currentTimeMillis() - startTime), responseBody);
        log.info("================  网关响应完成  =================\n");
        return content;
    }
}
