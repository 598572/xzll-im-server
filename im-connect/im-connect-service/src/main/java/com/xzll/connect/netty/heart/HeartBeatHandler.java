package com.xzll.connect.netty.heart;

import io.netty.channel.ChannelHandlerContext;

/**
 * Function:
 *
 * @author hzz
 * 019-01-20 17:15
 * @since JDK 1.8
 */
public interface HeartBeatHandler {

    /**
     * 处理心跳
     * @param ctx
     * @throws Exception
     */
    void process(ChannelHandlerContext ctx) throws Exception ;
}
