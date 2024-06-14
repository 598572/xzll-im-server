package com.xzll.gateway.filter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.ImServerAddressDTO;
import com.xzll.common.util.TraceIdUtil;
import com.xzll.gateway.config.nacos.NeedAddImServerUrlsConfig;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.xzll.common.constant.ImConstant.RedisKeyConstant.IM_SERVER_ROUND_COUNTER_KEY;
import static com.xzll.common.constant.ImConstant.RedisKeyConstant.NETTY_IP_PORT;

/**
 * @Author: hzz
 * @Date: 2024/6/12 15:57:21
 * @Description: 拦截并修改响应体 不得不说这个拦截我真的废了很大的劲 其实主要原因就是在于Order的设置，另外由于WebFilter比GlobalFilter的范围更广，所以这里的场景使用WebFilter，来实现修改响应的目的
 *  从controller出来就给他填充 所以优先级搞低  保证从controller出来后第一个到这个过滤器
 */
@Slf4j
@Component
@Order(value = 15)  //如果注解不生效则试试实现 Ordered接口！但是无论哪种 都得确保从controller出来后第一个进此Filter，否则可能走不到writeWith方法，或者响应过滤器拿不到设置的值。
public class ModifyResponseFilter implements WebFilter {//, Ordered

    @Resource
    private NeedAddImServerUrlsConfig needAddImServerUrlsConfig;
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestPath = exchange.getRequest().getPath().toString();
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        if (needAddImServerUrlsConfig.getUrls().contains(requestPath) && Objects.equals(originalResponse.getStatusCode(), HttpStatus.OK)) {
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    if (body instanceof Flux) {
                        Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                        return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                            TraceIdUtil.setTraceId(exchange.getAttribute(TraceIdUtil.TRACE_ID));
                            //数据过长时被截断，所以这里使用DataBufferFactory一次性join完所有数据，从而解决截断问题
                            DataBuffer join = bufferFactory.join(dataBuffers);
                            byte[] content = new byte[join.readableByteCount()];
                            join.read(content);
                            //释放掉内存
                            //此处不释放，而是在优先级最高的过滤器（响应时优先级最高的过滤器被最后执行）：日志打印过滤器释放
                            //DataBufferUtils.release(join);

                            String str = new String(content, StandardCharsets.UTF_8);
                            Map<String, Object> map = JSONUtil.toBean(str, TreeMap.class);
                            //填充im-server信息
                            fillImServerAddress(map);
                            originalResponse.getHeaders().setContentLength(JSONUtil.toJsonStr(map).getBytes().length);
                            return bufferFactory.wrap(JSONUtil.toJsonStr(map).getBytes());
                        }));

                    }
                    // 如果不是Flux
                    return super.writeWith(body);
                }
            };
            //替换响应体
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        }
        return chain.filter(exchange);
    }

    /**
     * 填充im服务地址
     *
     * @param needAddImServerMap
     */
    private void fillImServerAddress(Map<String, Object> needAddImServerMap) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(NETTY_IP_PORT);
        Assert.isTrue(!CollectionUtils.isEmpty(entries), "无长连接服务可用");

        List<ImServerAddressDTO> addressDTOS = entries.entrySet().stream().map(x -> {
            ImServerAddressDTO imServerAddressDTO = new ImServerAddressDTO();
            imServerAddressDTO.setIp(x.getKey().toString());
            imServerAddressDTO.setPort(Integer.valueOf(x.getValue().toString()));
            return imServerAddressDTO;
        }).collect(Collectors.toList());

        //todo 策略模式 支持多种负载算法 ：随机 hash 轮询

        //【随机】边界： 左闭 右开
        int randomIndex = ThreadLocalRandom.current().nextInt(0, addressDTOS.size());
        log.info("随机策略_randomIndex值:{}", randomIndex);
        ImServerAddressDTO randomResult = addressDTOS.get(randomIndex);

        //【轮询】
        int seatCount = addressDTOS.size();
        long current = redisTemplate.opsForValue().increment(IM_SERVER_ROUND_COUNTER_KEY) - 1;
        long index = current % seatCount;

        ImServerAddressDTO roundResult = addressDTOS.get((int) index);
        log.info("轮询策略_当前轮询值:{},index结果:{},胜出的ip端口:{}", current, index, JSONUtil.toJsonStr(roundResult));
        JSONObject jsonObject = JSONUtil.parseObj(needAddImServerMap.get("data"));
        Map<String, Object> ipPortMap = BeanUtil.beanToMap(roundResult, new TreeMap<>(), false, false);
        jsonObject.putAll(ipPortMap);
        needAddImServerMap.put("data", jsonObject);
    }
}