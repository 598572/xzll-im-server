package com.xzll.console.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: HBase 配置类
 */
@Slf4j
@Configuration
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
        config.set("hbase.zookeeper.quorum", zookeeperQuorum);
        config.set("hbase.zookeeper.property.clientPort", clientPort);
        config.set("hbase.master", hbaseMaster);
        config.set("hbase.client.operation.timeout", operationTimeout);
        config.set("hbase.client.scanner.timeout.period", scannerTimeout);
        config.set("hbase.client.retries.number", retriesNumber);

        log.info("Creating HBase connection with config: zookeeperQuorum={}, clientPort={}",
                zookeeperQuorum, clientPort);

        return ConnectionFactory.createConnection(config);
    }
} 