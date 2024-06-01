package com.xzll.connect.netty;


import cn.hutool.core.net.NetUtil;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.config.IMCenterServiceImplApolloConfig;
import com.xzll.connect.netty.channel.WebSocketChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.net.InetSocketAddress;

/**
 * @Author: hzz
 * @Date: 2024/6/1 10:19:34
 * @Description:
 */
@Slf4j
@Component
public class NettyServer implements CommandLineRunner {

    @Autowired
    private IMCenterServiceImplApolloConfig imCenterServiceImplApolloConfig;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

//    @PostConstruct
//    public void init() {
//        log.info("[NettyServer]_正在启动websocket服务器");
//        NioEventLoopGroup boss = new NioEventLoopGroup();
//        NioEventLoopGroup work = new NioEventLoopGroup();
//        try {
//            ServerBootstrap bootstrap = new ServerBootstrap();
//            bootstrap.group(boss, work);
//            bootstrap.channel(NioServerSocketChannel.class);
//            //TCP Keepalive 机制，实现 TCP 层级的心跳保活功能 用于保持长连接
//            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
//            //服务端 accept 队列的大小
//            bootstrap.option(ChannelOption.SO_BACKLOG, imCenterServiceImplApolloConfig.getSobacklog());
//            bootstrap.childHandler(new WebSocketChannelInitializer());
//
//            Channel channel = bootstrap.bind(new InetSocketAddress(imCenterServiceImplApolloConfig.getImServerPort())).sync().channel();
//            log.info("[NettyServer]_webSocket服务器启动成功：{}", channel);
//            // 开启channel监听器， 监听关闭动作
//            channel.closeFuture().sync();
//        } catch (InterruptedException e) {
//            log.error("[NettyServer]_运行出错：", e);
//        } finally {
//            boss.shutdownGracefully();
//            work.shutdownGracefully();
//            log.info("[NettyServer]_websocket服务器已关闭");
//        }
//    }

    @Override
    public void run(String... args) throws Exception {
        log.info("[NettyServer]_正在启动websocket服务器");
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup work = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, work);
            bootstrap.channel(NioServerSocketChannel.class);
            //TCP Keepalive 机制，实现 TCP 层级的心跳保活功能 用于保持长连接
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            //服务端 accept 队列的大小
            bootstrap.option(ChannelOption.SO_BACKLOG, imCenterServiceImplApolloConfig.getSobacklog());
            bootstrap.childHandler(new WebSocketChannelInitializer());

            String hostAddress = NetUtil.getLocalhost().getHostAddress();
            int usableLocalPort = NetUtil.getUsableLocalPort();
            //将来 每一个服务的ip和端口 是要注册到zk中 以便客户请求连接时进行路由
            redisTemplate.opsForHash().put("netty_ip_port", hostAddress, String.valueOf(usableLocalPort));
            //存储到本地，登录时 每一个用户对应一个机器的信息 <c1,s1> 保存到redis 使用 map存储
            NettyAttrUtil.setIpPort(hostAddress, usableLocalPort);
            Channel channel = bootstrap.bind(new InetSocketAddress(usableLocalPort)).sync().channel();
            log.info("[NettyServer]_webSocket服务器启动成功：{}", channel);
//            SocketAddress socketAddress = channel.localAddress();
//            System.out.println(socketAddress.toString());
            // 开启channel监听器， 监听关闭动作
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("[NettyServer]_运行出错：", e);
        } finally {
            boss.shutdownGracefully();
            work.shutdownGracefully();
            log.info("[NettyServer]_websocket服务器已关闭");
        }
    }
}
