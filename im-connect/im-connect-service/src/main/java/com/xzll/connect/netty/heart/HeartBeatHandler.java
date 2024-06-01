package com.xzll.connect.netty.heart;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author: hzz
 * @Date: 2024/6/1 17:26:24
 * @Description:
 */
public interface HeartBeatHandler {

    /**
     * 处理心跳
     * @param ctx
     * @throws Exception
     */
    void process(ChannelHandlerContext ctx) throws Exception ;
}
