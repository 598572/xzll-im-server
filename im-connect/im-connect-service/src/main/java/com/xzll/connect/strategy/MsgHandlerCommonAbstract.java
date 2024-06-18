package com.xzll.connect.strategy;


import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;


import com.xzll.common.pojo.BaseResponse;
import com.xzll.common.pojo.MsgBaseResponse;

import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.common.constant.UserRedisConstant;
import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
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
    public ReceiveUserDataDTO getReceiveUserDataTemplate(String toUserId, RedisTemplate<String, String> redisTemplate) {
        if (redisTemplate == null) {
            redisTemplate = SpringUtil.getBean(RedisTemplate.class);
        }
        if (null == redisTemplate) {
            return ReceiveUserDataDTO.builder().build();
        }
        ReceiveUserDataDTO build = null;
        try {
            Channel targetChannel = LocalChannelManager.getChannelByUserId(toUserId);
            String ipPort = (String) redisTemplate.opsForHash().get(UserRedisConstant.ROUTE_PREFIX, toUserId);
            String userStatus = (String) redisTemplate.opsForHash().get(UserRedisConstant.LOGIN_STATUS_PREFIX, toUserId);
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


    /**
     * 消息转发common
     *
     * @param requestUrl
     * @param tag
     * @param packet
     */
//    protected void msgTransferTemplate(String requestUrl, String tag, String packet) {
//        String response = HttpComponet.doPost(requestUrl, packet);
//        log.info((tag + "transfer_response:{}"), response);
//    }

    /**
     * 服务端接收消息后ack common 4 client
     *
     * @param ctx
     * @param packet
     */
    public void responseServerReceiveAckToSender(ChannelHandlerContext ctx, MsgBaseResponse packet) {
        try {
            if (Objects.nonNull(ctx) && Objects.nonNull(ctx.channel())) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(packet)));
            } else {
                log.info("responseServerReceiveAckToSender_传入的channel为空，不发送!");
            }
        } catch (Exception e) {
            log.error("responseServerReceiveAckToSender_异常!:", e);
        }
    }

    /**
     * 伪造ack给sender
     *
     * @param channel
     * @param packet
     */
    public void forgeClientReceiveAckToSender(Channel channel, MsgBaseResponse packet) {
        try {
            if (Objects.nonNull(channel)) {
                channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(packet)));
            } else {
                log.info("forgeClientReceiveAckToSender_传入的channel为空，不发送!");
            }
        } catch (Exception e) {
            log.error("forgeClientReceiveAckToSender_异常!:", e);
        }
    }

    /**
     * 返回消息接收ack给调用系统 4 system
     *
     * @param packet
     * @return
     */
    protected BaseResponse<MsgBaseResponse> responseServerReceiveAckToSystem(MsgBaseResponse packet, boolean receiveStatus) {
        return BaseResponse.returnResultSuccess("接收系统消息成功", packet);
    }

}
