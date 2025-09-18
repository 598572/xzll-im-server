package com.xzll.connect.netty;


import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.NetUtils;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.config.IMConnectServerConfig;
import com.xzll.connect.netty.channel.WebSocketChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.net.InetSocketAddress;


/**
 * Netty服务器启动类
 * 优化内容：
 * 1. 支持Epoll/NIO自适应选择
 * 2. 优化EventLoopGroup线程配置
 * 3. 完善TCP参数配置
 * 4. 增强异常处理和优雅关闭
 * 5. 简化IP获取逻辑
 * 
 * @Author: hzz
 * @Date: 2024/6/1 10:19:34
 * @Description:
 */
@Slf4j
@Component
public class NettyServer implements ApplicationRunner {

    @Resource
    private IMConnectServerConfig imConnectServerConfig;
    @Resource
    private RedissonUtils redissonUtils;
    
    /**
     * 本机mac 取en0，虚拟机centos7 取 enp0s3 ，docker 部署的话 取得就是容器的ip 直接使用 NetUtils.getRealIp()就行。应该是靠宿主机进行了桥接。所以无需手动指定了
     */
    @Value("${dubbo.network.interface.preferred:en0}")
    private String dubboPreferred;

    // EventLoopGroup实例，用于优雅关闭
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * 此逻辑最好在springboot项目都启动之后再启动，防止影响启动流程。 此前，我曾 在@PostConstruct、CommandLineRunner阶段启动此逻辑，将会遇到一些莫名问题
     *
     * @param args
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("[NettyServer]_正在启动websocket服务器");
        
        try {
            // 根据系统环境选择最优的EventLoopGroup实现
            initEventLoopGroups();
            
            ServerBootstrap bootstrap = new ServerBootstrap();
            configureServerBootstrap(bootstrap);
            
            // 获取绑定IP和端口
            String bindIp = getBindIp();
            int bindPort = imConnectServerConfig.getNettyPort();
            
            // 注册服务器信息到Redis
            registerServerInfo(bindIp, bindPort);
            
            // 启动服务器
            serverChannel = bootstrap.bind(new InetSocketAddress(bindPort)).sync().channel();
            log.info("[NettyServer]_WebSocket服务器启动成功：{}:{}", bindIp, bindPort);
            
            // 注册关闭监听器
            registerShutdownHook();

        } catch (Exception e) {
            log.error("[NettyServer]_启动失败：", e);
            shutdownGracefully();
        }
    }

    /**
     * 初始化EventLoopGroup，根据系统特性选择最优实现
     */
    private void initEventLoopGroups() {
        // 计算线程数量
        int bossThreads = 1; // Boss线程通常1个就够，用于接受连接
        int workerThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2); // Worker线程数量
        
        log.info("[NettyServer]_EventLoopGroup配置: Boss线程={}, Worker线程={}", bossThreads, workerThreads);
        
        // 在Linux环境下优先使用Epoll，性能更好
        if (Epoll.isAvailable()) {
            log.info("[NettyServer]_使用Epoll EventLoopGroup (Linux优化)");
            bossGroup = new EpollEventLoopGroup(bossThreads, 
                new DefaultThreadFactory("netty-boss", Thread.MAX_PRIORITY));
            workerGroup = new EpollEventLoopGroup(workerThreads, 
                new DefaultThreadFactory("netty-worker", Thread.NORM_PRIORITY));
        } else {
            log.info("[NettyServer]_使用NIO EventLoopGroup");
            bossGroup = new NioEventLoopGroup(bossThreads, 
                new DefaultThreadFactory("netty-boss", Thread.MAX_PRIORITY));
            workerGroup = new NioEventLoopGroup(workerThreads, 
                new DefaultThreadFactory("netty-worker", Thread.NORM_PRIORITY));
        }
    }

    /**
     * 配置ServerBootstrap
     */
    private void configureServerBootstrap(ServerBootstrap bootstrap) {
        bootstrap.group(bossGroup, workerGroup);
        
        // 根据EventLoopGroup类型选择对应的Channel实现
        if (Epoll.isAvailable()) {
            bootstrap.channel(EpollServerSocketChannel.class);
        } else {
            bootstrap.channel(NioServerSocketChannel.class);
        }
        
        // 配置ServerSocket选项
        bootstrap.option(ChannelOption.SO_BACKLOG, imConnectServerConfig.getSoBackLog())
                .option(ChannelOption.SO_REUSEADDR, true)  // 允许重用地址
                .option(ChannelOption.SO_RCVBUF, 32 * 1024) // 设置接收缓冲区大小32KB
                .option(ChannelOption.SO_SNDBUF, 32 * 1024); // 设置发送缓冲区大小32KB
        
        // 配置ChildChannel选项 (针对每个客户端连接)
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true)  // TCP Keepalive
                .childOption(ChannelOption.TCP_NODELAY, true)    // 禁用Nagle算法，适合实时通信
                .childOption(ChannelOption.SO_RCVBUF, 64 * 1024) // 客户端连接接收缓冲区64KB
                .childOption(ChannelOption.SO_SNDBUF, 64 * 1024) // 客户端连接发送缓冲区64KB
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                    new io.netty.channel.WriteBufferWaterMark(8 * 1024, 32 * 1024)) // 写缓冲区水位线
                .childOption(ChannelOption.SO_LINGER, 0);        // 关闭时立即释放端口
        
        // 设置Pipeline处理器
        bootstrap.childHandler(new WebSocketChannelInitializer());
        
        log.info("[NettyServer]_ServerBootstrap配置完成");
    }

    /**
     * 获取绑定IP地址
     */
    private String getBindIp() {
        // 简化IP获取逻辑，优先使用指定网卡，然后使用真实IP
        String specificIp = NetUtils.getSpecificInterfaceIp(dubboPreferred);
        String realIp = NetUtils.getRealIp();
        
        String bindIp = StringUtils.isNotBlank(specificIp) ? specificIp : realIp;
        log.info("[NettyServer]_获取到绑定IP: {} (指定网卡IP: {}, 真实IP: {})", bindIp, specificIp, realIp);
        
        return bindIp;
    }

    /**
     * 注册服务器信息到Redis
     */
    private void registerServerInfo(String ip, int port) {
        try {
            // 将服务器IP和端口注册到Redis，用于负载均衡
            redissonUtils.setHash(ImConstant.RedisKeyConstant.NETTY_IP_PORT, ip, String.valueOf(port));
            
            // 存储到本地工具类，用于其他组件获取
            NettyAttrUtil.setIpPort(ip, port);
            
            log.info("[NettyServer]_服务器信息已注册到Redis: {}:{}", ip, port);
        } catch (Exception e) {
            log.error("[NettyServer]_注册服务器信息到Redis失败", e);
        }
    }

    /**
     * 注册关闭钩子，确保优雅关闭
     */
    private void registerShutdownHook() {
        if (serverChannel != null) {
            serverChannel.closeFuture().addListener((ChannelFutureListener) channelFuture -> {
                log.info("[NettyServer]_服务器通道关闭，开始优雅关闭");
                shutdownGracefully();
            });
        }
        
        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[NettyServer]_接收到关闭信号，开始优雅关闭");
            shutdownGracefully();
        }, "netty-shutdown-hook"));
    }

    /**
     * 优雅关闭Netty服务器
     */
    @PreDestroy
    public void shutdownGracefully() {
        log.info("[NettyServer]_开始优雅关闭WebSocket服务器");
        
        try {
            // 关闭服务器通道
            if (serverChannel != null && serverChannel.isActive()) {
                serverChannel.close().sync();
                log.info("[NettyServer]_服务器通道已关闭");
            }
        } catch (InterruptedException e) {
            log.warn("[NettyServer]_关闭服务器通道被中断", e);
            Thread.currentThread().interrupt();
        }
        
        // 优雅关闭EventLoopGroup
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().addListener(future -> {
                if (future.isSuccess()) {
                    log.info("[NettyServer]_Boss EventLoopGroup已关闭");
                } else {
                    log.error("[NettyServer]_Boss EventLoopGroup关闭失败", future.cause());
                }
            });
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().addListener(future -> {
                if (future.isSuccess()) {
                    log.info("[NettyServer]_Worker EventLoopGroup已关闭");
                } else {
                    log.error("[NettyServer]_Worker EventLoopGroup关闭失败", future.cause());
                }
            });
        }
        
        log.info("[NettyServer]_WebSocket服务器优雅关闭完成");
    }

    /**
     * 获取服务器运行状态
     */
    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    /**
     * 获取当前绑定的端口
     */
    public int getPort() {
        if (serverChannel != null && serverChannel.localAddress() instanceof InetSocketAddress) {
            return ((InetSocketAddress) serverChannel.localAddress()).getPort();
        }
        return -1;
    }
}