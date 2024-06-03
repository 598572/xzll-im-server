package com.xzll.connect.netty.heart;


import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.NettyAttrUtil;

import com.xzll.connect.config.IMCenterServiceImplApolloConfig;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/6/1 17:30:01
 * @Description:
 */
@Slf4j
@Service
public class NettyServerHeartBeatHandlerImpl implements HeartBeatHandler {

    @Resource
    private IMCenterServiceImplApolloConfig imCenterServiceImplApolloConfig;

    @Override
    public void process(ChannelHandlerContext ctx) {

        long heartBeatTime = imCenterServiceImplApolloConfig.getHeartBeatTime() * 1000;

        Long lastReadTime = NettyAttrUtil.getReaderTime(ctx.channel());
        long now = System.currentTimeMillis();
        if (lastReadTime != null && now - lastReadTime > heartBeatTime) {
            String userId = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
            if (!StringUtils.isEmpty(userId)) {
                log.warn("客户端[{}]心跳超时[{}]ms，需要关闭连接!", userId, now - lastReadTime);
            }
            //routeHandler.userOffLine(userInfo, (NioSocketChannel) ctx.channel());
            //TODO 修改redis中该用户的上线状态
            ctx.channel().close();
        }
    }
}
