package com.xzll.connect.config;

import com.xzll.common.constant.ImConstant;

import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.netty.channel.LocalChannelManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: hzz
 * @Date: 2024/6/16 14:23:03
 * @Description: 关闭程序时 一些收尾处理（比如用户登录状态清理【很重要】，不清理的话大概率会登录状态不一致）
 */
@Slf4j
@Component
public class ShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 监听 ContextClosedEvent 事件的方法
     *
     * @param event
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        cleanUpResources();
    }

    /**
     * 关闭前的清理动作
     */
    private void cleanUpResources() {
        log.info("Spring Boot 应用正在关闭...");
        // 清理登录状态
        Set<String> allOnLineUserId = LocalChannelManager.getAllOnLineUserId();
        if (CollectionUtils.isEmpty(allOnLineUserId)) {
            return;
        }
        Map.Entry<String, Integer> ipPortMap = NettyAttrUtil.getIpPortMap();
        if (Objects.nonNull(ipPortMap) && StringUtils.isNotBlank(ipPortMap.getKey())) {
            redisTemplate.opsForHash().delete(ImConstant.RedisKeyConstant.NETTY_IP_PORT, ipPortMap.getKey());
        }
        allOnLineUserId.forEach(uid -> {
            //清除用户登录信息
            redisTemplate.opsForHash().delete(ImConstant.RedisKeyConstant.ROUTE_PREFIX, uid);
            //清除用户登录状态
            redisTemplate.opsForHash().delete(ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, uid);
        });
        log.info("共计{}个用户登录信息清除完毕", allOnLineUserId.size());
    }
}
