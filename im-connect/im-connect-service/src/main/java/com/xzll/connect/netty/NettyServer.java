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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.net.InetSocketAddress;


/**
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
    private RedisTemplate<String, Object> redisTemplate;
    /**
     * 本机mac 取en0，  虚拟机centos7 取 enp0s3 ，docker 部署的话 取得就是容器的ip 直接使用 NetUtils.getRealIp()就行。应该是靠宿主机进行了桥接。所以无需手动指定了
     */
    @Value("${dubbo.network.interface.preferred:en0}")
    private String dubboPreferred;

    /**
     * 此逻辑最好在springboot项目都启动之后再启动，防止影响启动流程。 此前，我曾 在@PostConstruct、CommandLineRunner阶段启动此逻辑，将会遇到一些莫名问题
     *
     * @param args
     * @throws Exception
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[NettyServer]_正在启动websocket服务器");
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup work = new NioEventLoopGroup();
        Channel channel = null;
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, work);
            bootstrap.channel(NioServerSocketChannel.class);
            //TCP Keepalive 机制，实现 TCP 层级的心跳保活功能 用于保持长连接
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            //服务端 accept 队列的大小
            bootstrap.option(ChannelOption.SO_BACKLOG, imConnectServerConfig.getSoBackLog());
            bootstrap.childHandler(new WebSocketChannelInitializer());

            String hostAddress = cn.hutool.core.net.NetUtil.getLocalhost().getHostAddress();
            String machineIpAddr = NetUtils.getMachineIpAddr();
            String realIp = NetUtils.getRealIp();

            //获取指定ip
            String dubboPreferredResult = NetUtils.getSpecificInterfaceIp(dubboPreferred);

            log.info("获取到的ip信息 hostAddress:{},machineIpAddr:{},realIp:{},enp0s3:{}", hostAddress, machineIpAddr, realIp,dubboPreferredResult);
//            int usableLocalPort = NetUtil.getUsableLocalPort();//测试时： 因为目前只有一台机器如果部署多个实例 需要放开此注释 即使用随机端口 保证端口不冲突
            int usableLocalPort = imConnectServerConfig.getPort();

            String realUseIp = StringUtils.isBlank(dubboPreferredResult) ? realIp : dubboPreferredResult;
            //将来 每一个服务的ip和端口 是要注册到zk中 以便客户请求连接时进行路由
            redisTemplate.opsForHash().put(ImConstant.RedisKeyConstant.NETTY_IP_PORT, realUseIp, String.valueOf(usableLocalPort));
            //存储到本地，登录时 每一个用户对应一个机器的信息 <c1,s1> 保存到redis 使用 map存储
            NettyAttrUtil.setIpPort(realUseIp, usableLocalPort);
            channel = bootstrap.bind(new InetSocketAddress(usableLocalPort)).sync().channel();
            log.info("[NettyServer]_webSocket服务器启动成功：{}", channel);

            //注意此处最好不要这么写，因为这样写的话线程到这里是阻塞了，在整合nacos的时候，因为nacos是在项目启动成功后进行监听配置中心的
            //而如果在这里阻塞 将走不到nacos的注册监听逻辑。所以在finally中异步并注册channle关闭监听器，从而做连接关闭后的收尾动作
            //channel.closeFuture().sync();

        } catch (Exception e) {
            log.error("[NettyServer]_运行出错：", e);
        } finally {
            // 开启channel监听器， 监听关闭动作
            assert channel != null;
            //连接关闭进行收尾工作
            channel.closeFuture().addListener((ChannelFutureListener) channelFuture -> {
                boss.shutdownGracefully();
                work.shutdownGracefully();
                log.info("[NettyServer]_websocket服务器已关闭");
            });
        }
    }
}
