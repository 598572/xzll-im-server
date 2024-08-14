package com.xzll.connect.dispatcher;



import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.base.ImBaseRequest;

import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;


/**
 * @Author: hzz
 * @Date: 2022/1/14 13:24:34
 * @Description:
 */
@Slf4j
@Component
public class HandlerDispatcher implements ApplicationContextAware {

    private Collection<MsgHandlerStrategy> exchangers;

    /**
     * 根据类型分发 通过长连接接收到的消息
     *
     * @param ctx
     * @param packet
     */
    public void dispatcher(ChannelHandlerContext ctx, ImBaseRequest packet) {
        if (CollectionUtils.isEmpty(exchangers)) {
            return;
        }
        for (MsgHandlerStrategy item : exchangers) {
            if (item.support(packet)) {
                item.exchange(ctx, packet);
            }
        }
    }

    /**
     * 系统调用分发
     *
     * @param packet
     * @return
     */
    public WebBaseResponse dispatcher(ImBaseRequest packet) {
        if (CollectionUtils.isEmpty(exchangers)) {
            return WebBaseResponse.returnResultError("无处理器");
        }
        for (MsgHandlerStrategy item : exchangers) {
            if (item.support(packet)) {
                return item.exchange(packet);
            }
        }
        return WebBaseResponse.returnResultError("无处理器");
    }

    /**
     * 转发到登录服务器，并发送
     *
     * @param msg
     */
    public WebBaseResponse receiveAndSendMsg(ImBaseRequest msg) {
        if (CollectionUtils.isEmpty(exchangers)) {
            return WebBaseResponse.returnResultError("无处理器");
        }
        for (MsgHandlerStrategy item : exchangers) {
            if (item.support(msg)) {
                return item.receiveAndSendMsg(msg);
            }
        }
        return WebBaseResponse.returnResultError("无处理器");
    }

    /**
     * 加载策略实现
     *
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        exchangers = applicationContext.getBeansOfType(MsgHandlerStrategy.class).values();
    }
}
