package com.xzll.connect.netty.heart;


import com.xzll.common.util.NettyAttrUtil;

import com.xzll.connect.config.IMCenterServiceImplApolloConfig;
import com.xzll.connect.netty.channel.ChannelManager;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Function:
 *
 * @author hzz
 * @since JDK 1.8
 */
@Slf4j
@Service
public class NettyServerHeartBeatHandlerImpl implements HeartBeatHandler {

    @Autowired
    private IMCenterServiceImplApolloConfig imCenterServiceImplApolloConfig;

    @Override
    public void process(ChannelHandlerContext ctx) {

        long heartBeatTime = imCenterServiceImplApolloConfig.getHeartBeatTime() * 1000;

        Long lastReadTime = NettyAttrUtil.getReaderTime(ctx.channel());
        long now = System.currentTimeMillis();
        if (lastReadTime != null && now - lastReadTime > heartBeatTime) {
            String userId = ChannelManager.getUserIdByChannelId(ctx.channel().id().asLongText());
            if (!StringUtils.isEmpty(userId)) {
                log.warn("客户端[{}]心跳超时[{}]ms，需要关闭连接!", userId, now - lastReadTime);
            }
            //routeHandler.userOffLine(userInfo, (NioSocketChannel) ctx.channel());
            //TODO 修改redis中该用户的上线状态
            ctx.channel().close();
        }
    }
}
