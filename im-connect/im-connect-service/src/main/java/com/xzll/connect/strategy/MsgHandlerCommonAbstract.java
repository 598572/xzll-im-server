package com.xzll.connect.strategy;


import cn.hutool.extra.spring.SpringUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.xzll.common.utils.RedissonUtils;

import java.util.Optional;

/**
 * @Author: hzz
 * @Date: 2022/1/20 18:25:30
 * @Description: 消息处理公共封装（仅用于 Protobuf）
 */
public abstract class MsgHandlerCommonAbstract {

    public static Logger log = LoggerFactory.getLogger(MsgHandlerCommonAbstract.class);


    /**
     * 获取接收人信息
     *
     * @param toUserId
     * @param redissonUtils
     * @return
     */
    public ReceiveUserDataDTO getReceiveUserDataTemplate(String toUserId, RedissonUtils redissonUtils) {
        if (redissonUtils == null) {
            redissonUtils = SpringUtil.getBean(RedissonUtils.class);
        }
        if (null == redissonUtils) {
            return ReceiveUserDataDTO.builder().build();
        }
        ReceiveUserDataDTO build = null;
        try {
            Channel targetChannel = LocalChannelManager.getChannelByUserId(toUserId);
            String ipPort = redissonUtils.getHash(ImConstant.RedisKeyConstant.ROUTE_PREFIX, toUserId);
            String userStatus = redissonUtils.getHash(ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, toUserId);
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
     * Protobuf 消息发送
     *
     * @param tag          日志标签
     * @param targetChannel 目标通道
     * @param protoBytes    Protobuf 序列化后的字节数组
     */
    protected void sendProtoMsg(String tag, Channel targetChannel, byte[] protoBytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(protoBytes);
        ChannelFuture future = targetChannel.writeAndFlush(new BinaryWebSocketFrame(buf));
        future.addListener((ChannelFutureListener) channelFuture ->
                log.info((tag + "Protobuf消息发送结果:{}"), channelFuture.isDone()));
    }

}
