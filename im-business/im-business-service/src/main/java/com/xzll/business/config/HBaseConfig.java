package com.xzll.business.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 
 * @Author: hzz
 * @Date:  2025/8/27 16:37:58
 * @Description: HBase 配置类，支持本地开发时禁用
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "hbase.enabled", havingValue = "true", matchIfMissing = false)
public class HBaseConfig {


    @Value("${hbase.zookeeper.quorum:120.46.85.43}")
    private String zookeeperQuorum;

    @Value("${hbase.zookeeper.property.clientPort:2181}")
    private String clientPort;

    @Value("${hbase.master:120.46.85.43:16000}")
    private String hbaseMaster;

    @Value("${hbase.client.operation.timeout:60000}")
    private String operationTimeout;

    @Value("${hbase.client.scanner.timeout.period:60000}")
    private String scannerTimeout;

    @Value("${hbase.client.retries.number:3}")
    private String retriesNumber;

    @Bean(destroyMethod = "close")
    public Connection hbaseConnection() throws IOException {
        org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
        
        // 基础连接配置
        config.set("hbase.zookeeper.quorum", zookeeperQuorum);
        config.set("hbase.zookeeper.property.clientPort", clientPort);
        config.set("hbase.master", hbaseMaster);
        
        // 超时配置
        config.set("hbase.client.operation.timeout", operationTimeout);
        config.set("hbase.client.scanner.timeout.period", scannerTimeout);
        config.set("hbase.client.retries.number", retriesNumber);
        
        // 连接池配置
        config.set("hbase.client.ipc.pool.type", "RoundRobinPool");
        config.set("hbase.client.ipc.pool.size", "10");
        config.set("hbase.client.connection.maxidletime", "60000");
        config.set("hbase.client.connection.threads.core", "20");
        config.set("hbase.client.connection.threads.max", "100");
        
        // 重试和容错配置
        config.set("hbase.client.pause", "1000");
        config.set("hbase.client.locate.region.retry.count", "10");
        config.set("hbase.client.locate.region.retry.delay", "500");
        config.set("hbase.client.locate.region.timeout", "30000");
        
        // 增强的连接重试配置
        config.set("hbase.client.connection.retry.count", "10");
        config.set("hbase.client.connection.retry.delay", "1000");
        config.set("hbase.client.connection.timeout", "30000");
        
        // 元数据操作配置
        config.set("hbase.client.meta.operation.timeout", "30000");
        config.set("hbase.client.meta.scanner.timeout.period", "30000");
        config.set("hbase.client.meta.retry.delay", "1000");
        config.set("hbase.client.meta.retry.number", "10");
        
        // 网络优化配置
        config.set("hbase.client.connection.threads.keepalivetime", "60000");
        config.set("hbase.client.connection.threads.maxidletime", "60000");
        
        // ZooKeeper配置
        config.set("zookeeper.session.timeout", "60000");
        config.set("zookeeper.recovery.retry", "5");
        config.set("zookeeper.recovery.retry.intervalmill", "1000");
        
        // 安全配置
        config.set("hbase.security.authentication", "simple");
        config.set("hbase.security.authorization", "false");

        log.info("Creating HBase connection with config: zookeeperQuorum={}, clientPort={}, master={}",
                zookeeperQuorum, clientPort, hbaseMaster);

        return ConnectionFactory.createConnection(config);
    }
}
