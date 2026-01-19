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
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
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
     * 
     * 配置来源（优先级从高到低）：
     * 1. Nacos配置：im.netty.bossThreads / im.netty.workerThreads
     * 2. 自动计算：Boss=max(1,CPU/4), Worker=CPU*2
     * 
     * 百万连接优化：
     * - Boss线程：1-2个，只负责接受连接
     * - Worker线程：CPU核心数 * 2，处理IO读写
     * - 使用Epoll（Linux）获得最佳性能
     */
    private void initEventLoopGroups() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 从配置读取线程数，0表示自动计算
        int bossThreads = imConnectServerConfig.getBossThreads();
        int workerThreads = imConnectServerConfig.getWorkerThreads();
        
        // 自动计算（配置为0时）
        if (bossThreads <= 0) {
            bossThreads = Math.max(1, cpuCores / 4);
        }
        if (workerThreads <= 0) {
            workerThreads = cpuCores * 2;
        }
        
        log.info("[NettyServer]_EventLoopGroup配置: CPU核心={}, Boss线程={}, Worker线程={} (来自Nacos配置)", 
            cpuCores, bossThreads, workerThreads);
        
        // 在Linux环境下优先使用Epoll，性能更好（百万连接必须使用Epoll）
        if (Epoll.isAvailable()) {
            log.info("[NettyServer]_使用Epoll EventLoopGroup (Linux优化，支持百万连接)");
            bossGroup = new EpollEventLoopGroup(bossThreads, 
                new DefaultThreadFactory("netty-boss", Thread.MAX_PRIORITY));
            workerGroup = new EpollEventLoopGroup(workerThreads, 
                new DefaultThreadFactory("netty-worker", Thread.NORM_PRIORITY));
        } else {
            log.warn("[NettyServer]_使用NIO EventLoopGroup（非Linux环境，性能受限）");
            bossGroup = new NioEventLoopGroup(bossThreads, 
                new DefaultThreadFactory("netty-boss", Thread.MAX_PRIORITY));
            workerGroup = new NioEventLoopGroup(workerThreads, 
                new DefaultThreadFactory("netty-worker", Thread.NORM_PRIORITY));
        }
    }

    /**
     * 配置ServerBootstrap（百万连接 + 高QPS 双优化）
     * 
     * 所有参数均可通过 Nacos 配置动态调整：
     * - im.netty.soBackLog: 连接队列大小
     * - im.netty.socketBufferSize: Socket缓冲区大小
     * - im.netty.writeBufferLowWaterMark: 写缓冲区低水位
     * - im.netty.writeBufferHighWaterMark: 写缓冲区高水位
     * 
     * 设计思路：在大量连接和高吞吐之间找到平衡
     * 
     * 内存估算：每连接约 bufferSize*2（收发缓冲区）
     * - 32KB配置：100万连接约 64GB
     * - 16KB配置：100万连接约 32GB
     */
    private void configureServerBootstrap(ServerBootstrap bootstrap) {
        bootstrap.group(bossGroup, workerGroup);
        
        // 根据EventLoopGroup类型选择对应的Channel实现
        if (Epoll.isAvailable()) {
            bootstrap.channel(EpollServerSocketChannel.class);
        } else {
            bootstrap.channel(NioServerSocketChannel.class);
        }
        
        // ==================== 从 Nacos 配置读取参数 ====================
        int soBacklog = imConnectServerConfig.getSoBackLog();
        int bufferSize = imConnectServerConfig.getSocketBufferSize();
        int writeBufferLow = imConnectServerConfig.getWriteBufferLowWaterMark();
        int writeBufferHigh = imConnectServerConfig.getWriteBufferHighWaterMark();
        
        // 配置校验和警告
        if (soBacklog < 65535) {
            log.warn("[NettyServer]_SO_BACKLOG={} 偏小，建议设置为65535以支持高并发", soBacklog);
        }
        if (bufferSize < 8 * 1024) {
            log.warn("[NettyServer]_socketBufferSize={}KB 偏小，可能影响QPS", bufferSize / 1024);
        }
        
        // ==================== Server Socket 选项 ====================
        bootstrap.option(ChannelOption.SO_BACKLOG, soBacklog)
                .option(ChannelOption.SO_REUSEADDR, true);
        
        // ==================== Child Channel 选项 ====================
        bootstrap
            // TCP 连接参数
            .childOption(ChannelOption.SO_KEEPALIVE, true)    // TCP Keepalive，检测死连接
            .childOption(ChannelOption.TCP_NODELAY, true)     // 【关键】禁用Nagle算法，降低延迟！
            .childOption(ChannelOption.SO_LINGER, 0)          // 关闭时立即释放端口
            
            // 缓冲区配置（从Nacos读取）
            .childOption(ChannelOption.SO_RCVBUF, bufferSize)  // 接收缓冲区
            .childOption(ChannelOption.SO_SNDBUF, bufferSize)  // 发送缓冲区
            
            // 写缓冲区水位线（从Nacos读取）
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                new io.netty.channel.WriteBufferWaterMark(writeBufferLow, writeBufferHigh))
            
            // 【关键】池化内存分配器 - 大幅提升高QPS性能
            .childOption(ChannelOption.ALLOCATOR, io.netty.buffer.PooledByteBufAllocator.DEFAULT)
            
            // 自动读取（高QPS场景保持开启）
            .childOption(ChannelOption.AUTO_READ, true);
        
        // 设置Pipeline处理器
        bootstrap.childHandler(new WebSocketChannelInitializer());
        
        log.info("[NettyServer]_ServerBootstrap配置完成 (来自Nacos): SO_BACKLOG={}, 缓冲区={}KB, 写水位线={}/{}KB", 
            soBacklog, bufferSize / 1024, writeBufferLow / 1024, writeBufferHigh / 1024);
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