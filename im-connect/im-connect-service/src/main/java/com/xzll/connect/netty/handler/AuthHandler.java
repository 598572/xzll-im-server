package com.xzll.connect.netty.handler;

import com.xzll.common.constant.ImConstant;
import com.xzll.connect.netty.channel.LocalChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

/**
 * @Author: hzz
 * @Date: 2022/6/8 17:26:24
 * @Description:
 */
@Slf4j
@ChannelHandler.Sharable
public class AuthHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            log.info("校验token");
            FullHttpRequest request = (FullHttpRequest) msg;
            HttpHeaders headers = request.headers();
            if (headers.size() < 1 || StringUtils.isEmpty(headers.get("token"))) {
                log.error("缺少必填参数token");
                ctx.channel().close();
                return;
            }
            String token = headers.get("token");
            String uid = headers.get("uid");//暂时这么写 待权限系统写好将从权限系统获取
            log.info("token:{}，uid:{}", token,uid);
            //todo 从权限系统根据token 获取userId
            LocalChannelManager.addUserChannel(uid,ctx.channel());
            ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
            //成功后移除该handler
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(msg);
        } else {
            log.info("该handler移除了，应该不会进入该分支");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //todo 移除该channel
        ctx.channel().close();
    }
}
