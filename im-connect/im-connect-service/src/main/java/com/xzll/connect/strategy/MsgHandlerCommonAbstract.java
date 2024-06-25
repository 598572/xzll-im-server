package com.xzll.connect.strategy;


import cn.hutool.extra.spring.SpringUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

/**
 * @Author: hzz
 * @Date: 2022/1/20 18:25:30
 * @Description: 消息处理公共封装
 */
public abstract class MsgHandlerCommonAbstract implements MsgHandlerStrategy {

    public static Logger log = LoggerFactory.getLogger(MsgHandlerCommonAbstract.class);


    /**
     * 获取接收人信息
     *
     * @param toUserId
     * @param redisTemplate
     * @return
     */
    public ReceiveUserDataDTO getReceiveUserDataTemplate(String toUserId, RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate == null) {
            redisTemplate = SpringUtil.getBean("redisTemplate", RedisTemplate.class);
        }
        if (null == redisTemplate) {
            return ReceiveUserDataDTO.builder().build();
        }
        ReceiveUserDataDTO build = null;
        try {
            Channel targetChannel = LocalChannelManager.getChannelByUserId(toUserId);
            String ipPort = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.ROUTE_PREFIX, toUserId);
            String userStatus = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, toUserId);
            build = ReceiveUserDataDTO.builder()
                    .channelIdByUserId(targetChannel != null ? targetChannel.id().asLongText() : null)
                    .targetChannel(targetChannel)
                    .userStatus(userStatus)
                    .routeAddress(ipPort)
                    .build();
        } catch (Exception e) {
            log.error("getReceiveUserData_获取接收者信息失败 toUserId:{},e:", toUserId, e);
        }
        return Optional.ofNullable(build).orElse(ReceiveUserDataDTO.builder().build());
    }


    /**
     * 消息发送common
     *
     * @param tag
     * @param targetChannel
     * @param packet
     * @param
     */
    protected void msgSendTemplate(String tag, Channel targetChannel, String packet) {
        ChannelFuture future = targetChannel.writeAndFlush(new TextWebSocketFrame(packet));
        future.addListener((ChannelFutureListener) channelFuture ->
                log.info((tag + "接收者在线直接发送,消息结果:{}"), channelFuture.isDone()));
    }

}
