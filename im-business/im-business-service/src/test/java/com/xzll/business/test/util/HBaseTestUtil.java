package com.xzll.business.test.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * HBase测试工具类
 * 提供通用的配置和解析器
 */
public class HBaseTestUtil {
    
    // 常量配置
    public static final String ZOOKEEPER_QUORUM = "120.46.85.43";
    public static final int ZOOKEEPER_PORT = 2181;
    public static final String HBASE_MASTER = "120.46.85.43";
    public static final String TEST_TABLE_NAME = "hahhahah";
    public static final String COLUMN_FAMILY = "cf";
    
    /**
     * 自定义RegionServer地址解析器
     */
    public static class RegionServerAddressResolver {
        
        private static final Map<String, Integer> FRP_PORT_MAPPING = new HashMap<>();
        
        static {
            // vm_130 (192.168.1.130) -> hadoop01:16029
            // vm_131 (192.168.1.131) -> hadoop02:16020  
            // vm_132 (192.168.1.132) -> hadoop03:16022
            FRP_PORT_MAPPING.put("hadoop01", 16029);
            FRP_PORT_MAPPING.put("hadoop02", 16020);
            FRP_PORT_MAPPING.put("hadoop03", 16022);
        }
        
        public String resolveRegionServerAddress(String originalHost) {
            String hostname = originalHost;
            if (originalHost.contains(":")) {
                hostname = originalHost.split(":")[0];
            }
            
            Integer mappedPort = FRP_PORT_MAPPING.get(hostname);
            if (mappedPort != null) {
                return hostname + ":" + mappedPort;
            }
            return originalHost;
        }
        
        public String[] getAvailableRegionServerAddresses() {
            return FRP_PORT_MAPPING.entrySet().stream()
                    .map(entry -> entry.getKey() + ":" + entry.getValue())
                    .toArray(String[]::new);
        }
        
        public void printPortMapping() {
            System.out.println("=== FRP端口映射配置 ===");
            System.out.println("vm_130 (192.168.1.130) -> hadoop01:16029");
            System.out.println("vm_131 (192.168.1.131) -> hadoop02:16020");
            System.out.println("vm_132 (192.168.1.132) -> hadoop03:16022");
            System.out.println("=======================");
        }
    }
    
    /**
     * 获取使用自定义解析器的HBase配置
     */
    public static Configuration getHBaseConfigWithResolver() {
        Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM);
        config.set("hbase.zookeeper.property.clientPort", String.valueOf(ZOOKEEPER_PORT));
        config.set("hbase.master", HBASE_MASTER);
        
        // 使用自定义解析器获取RegionServer地址
        RegionServerAddressResolver resolver = new RegionServerAddressResolver();
        String[] regionServerAddresses = resolver.getAvailableRegionServerAddresses();
        
        for (int i = 0; i < regionServerAddresses.length; i++) {
            config.set("hbase.regionserver.hostname." + i, regionServerAddresses[i]);
        }
        
        // 基础优化配置
        config.set("hbase.client.retries.number", "15");
        config.set("hbase.client.pause", "1000");
        config.set("hbase.client.operation.timeout", "30000");
        config.set("hbase.client.scanner.timeout.period", "30000");
        
        // 禁用缓存，强制实时查找
        config.set("hbase.client.cache.region", "false");
        config.set("hbase.client.cache.meta", "false");
        config.set("hbase.client.cache.config", "false");
        
        // Region查找重试配置
        config.set("hbase.client.locate.region.retry.count", "20");
        config.set("hbase.client.locate.region.retry.delay", "500");
        config.set("hbase.client.locate.region.timeout", "30000");
        
        // 连接重试配置
        config.set("hbase.client.connection.retry.count", "15");
        config.set("hbase.client.connection.retry.delay", "500");
        
        // 元数据操作配置
        config.set("hbase.client.meta.operation.timeout", "30000");
        config.set("hbase.client.meta.scanner.timeout.period", "30000");
        config.set("hbase.client.meta.retry.delay", "1000");
        config.set("hbase.client.meta.retry.number", "15");
        
        // ZooKeeper配置
        config.set("zookeeper.session.timeout", "60000");
        config.set("zookeeper.recovery.retry", "10");
        config.set("zookeeper.recovery.retry.intervalmill", "1000");
        
        return config;
    }
    
    /**
     * 获取HBase连接
     */
    public static Connection getConnection() throws Exception {
        Configuration config = getHBaseConfigWithResolver();
        return ConnectionFactory.createConnection(config);
    }
} 