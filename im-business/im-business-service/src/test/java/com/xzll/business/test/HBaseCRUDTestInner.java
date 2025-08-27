package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/16 14:08:54
 * @Description: HBase CRUD操作测试类
 */
@Slf4j
public class HBaseCRUDTestInner {

    // ==================== 配置参数 ====================
    // 请根据您的实际环境修改这些参数
    private static final String ZOOKEEPER_QUORUM = "192.168.1.131";  // 修改为您的ZooKeeper地址
    private static final int ZOOKEEPER_PORT = 2181;              // 修改为您的ZooKeeper端口
    private static final String HBASE_MASTER = "192.168.1.130"; // 修改为您的HBase Master地址


//    private static final String ZOOKEEPER_QUORUM = "120.46.85.43";  // 修改为您的ZooKeeper地址
//    private static final int ZOOKEEPER_PORT = 2181;              // 修改为您的ZooKeeper端口
//    private static final String HBASE_MASTER = "120.46.85.43"; // 修改为您的HBase Master地址

    // 测试表配置
    private static final String TEST_TABLE_NAME = "HZZ_MK";
    private static final String COLUMN_FAMILY = "cf";
    private static final String NAME_COLUMN = "name";
    private static final String AGE_COLUMN = "age";
    private static final String EMAIL_COLUMN = "email";



    /**
     * 获取HBase配置
     */
    private Configuration getHBaseConfig() {
        Configuration config = HBaseConfiguration.create();
        
        // 基本连接配置
        config.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM);
        config.set("hbase.zookeeper.property.clientPort", String.valueOf(ZOOKEEPER_PORT));
        config.set("hbase.master", HBASE_MASTER);
        
        // 禁用自动发现，强制使用指定地址
//        config.set("hbase.client.discovery.enabled", "false");
//        config.set("hbase.master.dns.interface", "default");
//        config.set("hbase.regionserver.dns.interface", "default");
        
        // 连接超时配置
        config.set("hbase.client.operation.timeout", "60000");        // 操作超时60秒
        config.set("hbase.client.scanner.timeout.period", "60000");   // 扫描超时60秒
        config.set("hbase.client.retries.number", "5");              // 重试次数
        config.set("hbase.client.pause", "2000");                    // 重试间隔2秒
        
        // ZooKeeper超时配置
        config.set("zookeeper.session.timeout", "60000");            // 会话超时60秒
        config.set("zookeeper.recovery.retry", "5");                 // 重试次数
        config.set("zookeeper.recovery.retry.intervalmill", "2000"); // 重试间隔2秒
        config.set("zookeeper.request.timeout", "60000");            // 请求超时60秒
        
        // 禁用Kerberos认证（如果不需要）
        config.set("hbase.security.authentication", "simple");
        config.set("hbase.security.authorization", "false");
        
        // 网络优化配置（针对FRP环境）
        config.set("hbase.client.connection.maxidletime", "60000");   // 连接最大空闲时间
        config.set("hbase.client.connection.threads.core", "20");     // 核心连接线程数
        config.set("hbase.client.connection.threads.max", "100");     // 最大连接线程数
        config.set("hbase.client.connection.threads.keepalivetime", "60000"); // 连接保活时间
        
        // 禁用DNS缓存，避免解析问题
//        config.set("hbase.client.dns.interface", "default");
//        config.set("hbase.client.dns.nameserver", "8.8.8.8");

        // 直接指定所有 RegionServer 地址（通过 FRP 映射的地址）
//        config.set("hbase.client.ipc.pool.type", "RoundRobinPool");
        config.set("hbase.client.ipc.pool.size", "3");
//        config.set("hbase.regionserver.port", "16020"); // 基础端口
//
//        // 设置所有 RegionServer 地址
//        config.set("hbase.regionserver.address.0", PUBLIC_HOST+":16020"); // hadoop01
//        config.set("hbase.regionserver.address.1", PUBLIC_HOST+":16021"); // hadoop02
//        config.set("hbase.regionserver.address.2", PUBLIC_HOST+":16022"); // hadoop03




//         直接指定所有 RegionServer 地址（通过 FRP 映射的地址）
        config.set("hbase.client.ipc.pool.type", "RoundRobinPool");
        config.set("hbase.client.ipc.pool.size", "3");
        config.set("hbase.regionserver.port", "16020"); // 基础端口

        // 设置所有 RegionServer 地址
        config.set("hbase.regionserver.address.0", "192.168.1.130:16020"); // hadoop01
        config.set("hbase.regionserver.address.1", "192.168.1.131:16020"); // hadoop02
        config.set("hbase.regionserver.address.2", "192.168.1.132:16020"); // hadoop03


        log.info("HBase配置: ZooKeeper={}:{}, Master={}", ZOOKEEPER_QUORUM, ZOOKEEPER_PORT, HBASE_MASTER);
        return config;
    }

    @Test
    public void testHBaseConnection() throws IOException {
        log.info("=== 测试HBase连接 ===");
        
        // 设置系统属性来覆盖HBase内部超时
        System.setProperty("hbase.client.operation.timeout", "60000");
        System.setProperty("hbase.client.scanner.timeout.period", "60000");
        System.setProperty("hbase.client.retries.number", "5");
        System.setProperty("hbase.client.pause", "2000");
        System.setProperty("zookeeper.session.timeout", "60000");
        System.setProperty("zookeeper.recovery.retry", "5");
        System.setProperty("zookeeper.recovery.retry.intervalmill", "2000");
        System.setProperty("zookeeper.request.timeout", "60000");
        
        log.info("正在连接到: ZooKeeper={}:{}, Master={}", ZOOKEEPER_QUORUM, ZOOKEEPER_PORT, HBASE_MASTER);
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            log.info("✅ HBase连接成功");
            
            // 获取表列表
            TableName[] tableNames = admin.listTableNames();
            log.info("📋 现有表数量: {}", tableNames.length);
            
            for (TableName tableName : tableNames) {
                log.info("  - {}", tableName.getNameAsString());
            }
            
        } catch (Exception e) {
            log.error("❌ HBase连接失败", e);
            log.error("请检查以下配置:");
            log.error("1. ZooKeeper地址: {}:{}", ZOOKEEPER_QUORUM, ZOOKEEPER_PORT);
            log.error("2. HBase Master地址: {}", HBASE_MASTER);
            log.error("3. 网络连接是否正常");
            log.error("4. HBase集群是否已启动");
            throw e;
        }
    }

    /**
     * 等待表上线
     */
    private void waitForTableOnline(Admin admin, String tableName) throws IOException {
//        TableName tn = TableName.valueOf(tableName);
//        int maxWait = 30; // 最多等待30秒
//
//        for (int i = 0; i < maxWait; i++) {
//            if (admin.isTableEnabled(tn)) {
//                log.info("✅ 表 {} 已上线", tableName);
//                return;
//            }
//            log.info("⏳ 等待表 {} 上线... ({}/{})", tableName, i + 1, maxWait);
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                break;
//            }
//        }
//
//        if (!admin.isTableEnabled(tn)) {
//            throw new IOException("表 " + tableName + " 上线超时");
//        }
    }

    @Test
    public void testHBaseCRUD() throws IOException {
        log.info("=== 测试HBase CRUD操作 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 创建测试表
            createTestTable(admin);
            
            // 等待表上线
            waitForTableOnline(admin, TEST_TABLE_NAME);
            
            // 获取表对象
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                
                // 测试插入数据
                testInsertData(table);
                
                // 测试查询数据
                testQueryData(table);
                
                // 测试更新数据
                testUpdateData(table);
                
                // 测试删除数据
                testDeleteData(table);
                
                // 测试批量操作
                testBatchOperations(table);
                
                // 测试扫描操作
                testScanOperations(table);
                
            }
            
            // 清理测试表
            cleanupTestTable(admin);
            
        } catch (Exception e) {
            log.error("❌ HBase CRUD测试失败", e);
            throw e;
        }
        
        log.info("✅ HBase CRUD测试完成");
    }

    /**
     * 创建测试表
     */
    private void createTestTable(Admin admin) throws IOException {
        TableName tableName = TableName.valueOf(TEST_TABLE_NAME);
        
        if (admin.tableExists(tableName)) {
            log.info("📋 测试表已存在: {}", TEST_TABLE_NAME);
            // 确保表已启用
            if (!admin.isTableEnabled(tableName)) {
                log.info("🔄 启用已存在的表: {}", TEST_TABLE_NAME);
                admin.enableTable(tableName);
            }
            return;
        }
        
        log.info("🏗️ 开始创建测试表: {}", TEST_TABLE_NAME);
        TableDescriptorBuilder tableDescriptorBuilder = TableDescriptorBuilder.newBuilder(tableName);
        ColumnFamilyDescriptor columnFamilyDescriptor = ColumnFamilyDescriptorBuilder
                .newBuilder(Bytes.toBytes(COLUMN_FAMILY))
                .build();
        
        tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
        admin.createTable(tableDescriptorBuilder.build());
        log.info("✅ 测试表创建成功: {}", TEST_TABLE_NAME);
        
        // 等待表创建完成
        try {
            Thread.sleep(2000); // 等待2秒让表完全创建
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ 等待表创建完成被中断");
        }
    }

    /**
     * 清理测试表
     */
    private void cleanupTestTable(Admin admin) throws IOException {
//        TableName tableName = TableName.valueOf(TEST_TABLE_NAME);
//        if (admin.tableExists(tableName)) {
//            admin.disableTable(tableName);
//            admin.deleteTable(tableName);
//            log.info("🧹 测试表已删除: {}", TEST_TABLE_NAME);
//        }
    }

    /**
     * 测试插入数据
     */
    private void testInsertData(Table table) throws IOException {
        log.info("--- 测试插入数据 ---");
        
        String rowKey = "user_002"+ RandomUtil.randomNumbers(10);
        String name = "张三";
        int age = 25;
        String email = "zhangsan@example.com";
        
        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN), Bytes.toBytes(name));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN), Bytes.toBytes(age));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN), Bytes.toBytes(email));
        
        table.put(put);
        log.info("✅ 插入数据成功: rowKey={}, name={}, age={}, email={}", rowKey, name, age, email);
    }

    /**
     * 测试查询数据
     */
    private void testQueryData(Table table) throws IOException {
        log.info("--- 测试查询数据 ---");
        
        String rowKey = "user_001";
        Get get = new Get(Bytes.toBytes(rowKey));
        Result result = table.get(get);
        
        if (!result.isEmpty()) {
            String name = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN)));
            int age = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN)));
            String email = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN)));
            
            log.info("📖 查询结果: rowKey={}, name={}, age={}, email={}", rowKey, name, age, email);
        } else {
            log.warn("⚠️ 未找到数据: rowKey={}", rowKey);
        }
    }

    /**
     * 测试更新数据
     */
    private void testUpdateData(Table table) throws IOException {
        log.info("--- 测试更新数据 ---");
        
        String rowKey = "user_001";
        String newName = "张三(已更新)";
        int newAge = 26;
        
        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN), Bytes.toBytes(newName));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN), Bytes.toBytes(newAge));
        
        table.put(put);
        log.info("✅ 更新数据成功: rowKey={}, newName={}, newAge={}", rowKey, newName, newAge);
        
        // 验证更新结果
        Get get = new Get(Bytes.toBytes(rowKey));
        Result result = table.get(get);
        String updatedName = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN)));
        int updatedAge = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN)));
        
        log.info("🔍 更新验证: name={}, age={}", updatedName, updatedAge);
    }

    /**
     * 测试删除数据
     */
    private void testDeleteData(Table table) throws IOException {
        log.info("--- 测试删除数据 ---");
        
        String rowKey = "user_001";
        
        // 删除整行数据
        Delete delete = new Delete(Bytes.toBytes(rowKey));
        table.delete(delete);
        log.info("🗑️ 删除数据成功: rowKey={}", rowKey);
        
        // 验证删除结果
        Get get = new Get(Bytes.toBytes(rowKey));
        Result result = table.get(get);
        
        if (result.isEmpty()) {
            log.info("✅ 数据删除验证成功: rowKey={}", rowKey);
        } else {
            log.warn("⚠️ 数据删除验证失败: rowKey={}", rowKey);
        }
    }

    /**
     * 测试批量操作
     */
    private void testBatchOperations(Table table) throws IOException {
        log.info("--- 测试批量操作 ---");
        
        List<Put> puts = new ArrayList<>();
        
        // 准备批量数据
        for (int i = 1; i <= 5; i++) {
            String rowKey = "batch_user_" + String.format("%03d", i);
            String name = "批量用户" + i;
            int age = 20 + i;
            String email = "batch" + i + "@example.com";
            
            Put put = new Put(Bytes.toBytes(rowKey));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN), Bytes.toBytes(name));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN), Bytes.toBytes(age));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN), Bytes.toBytes(email));
            
            puts.add(put);
        }
        
        // 批量插入
        Object[] results = new Object[puts.size()];
        try {
            table.batch(puts, results);
            log.info("✅ 批量插入成功: {}条数据", puts.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ 批量插入被中断", e);
        }
        
        // 验证批量插入结果
        int successCount = 0;
        for (int i = 1; i <= 5; i++) {
            String rowKey = "batch_user_" + String.format("%03d", i);
            Get get = new Get(Bytes.toBytes(rowKey));
            Result result = table.get(get);
            
            if (!result.isEmpty()) {
                successCount++;
            }
        }
        
        log.info("📊 批量插入验证: 成功{}条", successCount);
    }

    /**
     * 测试扫描操作
     */
    private void testScanOperations(Table table) throws IOException {
        log.info("--- 测试扫描操作 ---");
        
        // 扫描表数据
        Scan scan = new Scan();
        scan.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN));
        scan.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN));
        
        ResultScanner scanner = table.getScanner(scan);
        int count = 0;
        
        for (Result result : scanner) {
            count++;
            String rowKey = Bytes.toString(result.getRow());
            String name = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN)));
            int age = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN)));
            
            log.info("📖 扫描数据 {}: rowKey={}, name={}, age={}", count, rowKey, name, age);
        }
        
        scanner.close();
        log.info("📊 扫描完成: 共{}条数据", count);
    }
} 