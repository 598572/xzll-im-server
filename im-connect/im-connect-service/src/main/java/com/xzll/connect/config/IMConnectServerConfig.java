package com.xzll.connect.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import javax.annotation.PostConstruct;

@Slf4j
@Setter
@Getter
@RefreshScope
@ConfigurationProperties(prefix = "im.netty")
public class IMConnectServerConfig {

    /**
     * netty端口
     */
    private int nettyPort;

    /**
     * prometheus采集指标 端口
     */
    private int prometheusPort;
    /**
     * accept 队列的大小
     */
    private int soBackLog;
    /**
     * 在调试期加入日志功能，从而可以打印出报文的请求和响应细节
     */
    private boolean debug;
    
    /**
     * IdleStateHandler检测周期（秒）- 默认30秒
     * 用于触发READER_IDLE事件，应该 < heartBeatTime
     * 推荐配置：idleStateCheckInterval(30秒) < heartBeatTime(45秒)
     */
    private int idleStateCheckInterval = 30;
    
    /**
     * 心跳超时时间（秒）- 默认45秒
     * 客户端需要在这个时间内发送心跳，否则连接会被关闭
     * 注意：应该 > IdleStateHandler检测周期（idleStateCheckInterval），以提供容错余量
     * 推荐配置：idleStateCheckInterval(30秒) < heartBeatTime(45秒)
     */
    private long heartBeatTime = 45;
    
    /**
     * 最大心跳失败次数 - 默认3次
     * 连续失败达到这个次数后，关闭连接
     */
    private int maxHeartbeatFailures = 3;
    
    /**
     * 主动心跳发送间隔（秒）- 默认25秒
     * 服务端主动向客户端发送ping的间隔
     */
    private int activeHeartbeatInterval = 25;
    
    // ==================== Netty 线程配置 ====================
    
    /**
     * Boss线程数 - 默认1
     * Boss线程只负责接受连接，1-2个足够
     * 设置为0时自动计算：max(1, CPU核数/4)
     */
    private int bossThreads = 0;
    
    /**
     * Worker线程数 - 默认0（自动）
     * Worker线程负责IO读写，建议 CPU核数 * 2
     * 设置为0时自动计算：CPU核数 * 2
     */
    private int workerThreads = 0;
    
    // ==================== Netty 缓冲区配置（高QPS优化） ====================
    
    /**
     * Socket接收/发送缓冲区大小（字节）- 默认32KB
     * 平衡连接数和吞吐量：
     * - 小值(8KB)：省内存，支持更多连接，但QPS较低
     * - 大值(64KB)：高吞吐，但内存占用增加
     * - 推荐32KB：平衡点
     */
    private int socketBufferSize = 32 * 1024;
    
    /**
     * 写缓冲区低水位线（字节）- 默认32KB
     * 当写缓冲区低于此值时，恢复写入
     */
    private int writeBufferLowWaterMark = 32 * 1024;
    
    /**
     * 写缓冲区高水位线（字节）- 默认128KB
     * 当写缓冲区高于此值时，暂停写入，触发channelWritabilityChanged
     * 防止消息堆积导致OOM
     */
    private int writeBufferHighWaterMark = 128 * 1024;
    
    /**
     * 是否启用WebSocket压缩 - 默认false
     * 高QPS场景建议关闭，压缩消耗CPU
     * 低带宽场景可开启以节省流量
     */
    private boolean enableCompression = false;

    /**
     * 配置验证：确保 idleStateCheckInterval < heartBeatTime
     * 启动时自动验证配置合理性，避免配置冲突
     */
    @PostConstruct
    public void validateConfig() {
        if (idleStateCheckInterval >= heartBeatTime) {
            String errorMsg = String.format(
                "❌ 心跳配置错误：idleStateCheckInterval(%d秒) 必须 < heartBeatTime(%d秒)，请检查配置！",
                idleStateCheckInterval, heartBeatTime);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        // 计算容错余量
        long tolerance = heartBeatTime - idleStateCheckInterval;
        log.info("✅ 心跳配置验证通过：idleStateCheckInterval={}秒, heartBeatTime={}秒, 容错余量={}秒",
            idleStateCheckInterval, heartBeatTime, tolerance);
    }
}
