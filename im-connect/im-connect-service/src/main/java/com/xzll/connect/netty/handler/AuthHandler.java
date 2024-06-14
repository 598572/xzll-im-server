package com.xzll.connect.netty.handler;

import cn.hutool.core.lang.Assert;
import cn.hutool.extra.spring.SpringUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.AnswerCode;
import com.xzll.connect.netty.channel.LocalChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2022/6/8 17:26:24
 * @Description:
 */
@Slf4j
@ChannelHandler.Sharable
public class AuthHandler extends ChannelInboundHandlerAdapter {

    private static final RedisTemplate<String, String> redisTemplate = SpringUtil.getBean("redisTemplate", RedisTemplate.class);


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){
        if (msg instanceof FullHttpRequest) {
            //校验token
            FullHttpRequest request = (FullHttpRequest) msg;
            HttpHeaders headers = request.headers();
            if (Objects.isNull(headers) || headers.isEmpty() || StringUtils.isEmpty(headers.get(ImConstant.TOKEN))) {
                log.error("缺少必填参数token,将关闭连接");
                ctx.channel().close();
                return;
            }
            String token = headers.get(ImConstant.TOKEN);
            String uid = redisTemplate.opsForValue().get(ImConstant.RedisKeyConstant.USER_TOKEN_KEY + token);
            log.info("token:{}，uid:{}", token, uid);
            Assert.isTrue(StringUtils.isNotBlank(uid), AnswerCode.TOKEN_INVALID.getMessage());
            //从redis 根据token获取uid 如果token不存在说明未登录，让客户端使用refreshToken重新获取token， 重试建立连接。
            LocalChannelManager.addUserChannel(uid, ctx.channel());
            ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
            //成功后移除该handler
            ctx.pipeline().remove(this);
            //往下走
            ctx.fireChannelRead(msg);
        } else {
            log.info("该handler移除了，应该不会进入该分支");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        //发生异常
        ctx.channel().close();
    }
}
