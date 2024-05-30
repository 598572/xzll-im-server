package com.xzll.connect.strategy;



import com.xzll.common.pojo.BaseResponse;
import com.xzll.common.pojo.MsgBaseRequest;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author: hzz
 * @Date: 2022/1/14 13:25:30
 * @Description: 消息处理策略定义
 */
public interface MsgHandlerStrategy {


    //---------------------------------------------common-----------------------------------------------------

    /**
     * 适配
     */
    boolean support(MsgBaseRequest.MsgType type);

    //---------------------------------------------长连接-----------------------------------------------------

    /**
     * 处理通过长连接发送过来的消息
     */
    default void exchange(ChannelHandlerContext ctx, MsgBaseRequest packet) {
    }

    /**
     * send 消息投递至登录服务器(http)
     * <p>
     * 处理接收到的跳转请求
     */
    default BaseResponse receiveAndSendMsg(MsgBaseRequest msg) {
        return null;
    }


    //---------------------------------------------http发送消息(通过企业总线方式)-----------------------------------------------------

    /**
     * 处理系统消息(http请求)
     *
     * @param packet
     */
    default BaseResponse exchange(MsgBaseRequest packet) {
        return BaseResponse.returnResultSuccess();
    }

}
