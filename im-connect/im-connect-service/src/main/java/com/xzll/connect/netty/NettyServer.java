package com.xzll.connect.netty;



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
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;

@Slf4j
@Component
public class NettyServer  implements CommandLineRunner{

    @Autowired
    private IMCenterServiceImplApolloConfig imCenterServiceImplApolloConfig;

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

            Channel channel = bootstrap.bind(new InetSocketAddress(imCenterServiceImplApolloConfig.getImServerPort())).sync().channel();
            log.info("[NettyServer]_webSocket服务器启动成功：{}", channel);
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
