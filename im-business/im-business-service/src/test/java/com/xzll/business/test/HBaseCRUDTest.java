package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.hbase.client.RegionInfo;
import java.util.HashMap;
import java.util.Map;

import com.xzll.business.test.util.HBaseTestUtil;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.conf.Configuration;

/**
 * @Author: hzz
 * @Date: 2024/6/16 14:08:54
 * @Description: HBase CRUD操作测试类 - 已适配新版本HBase客户端
 */
public class HBaseCRUDTest {

    // ==================== 配置参数 ====================
    // 请根据您的实际环境修改这些参数
    private static final String ZOOKEEPER_QUORUM = "120.46.85.43";  // 修改为您的ZooKeeper地址
    private static final int ZOOKEEPER_PORT = 2181;              // 修改为您的ZooKeeper端口
    private static final String HBASE_MASTER = "120.46.85.43"; // 修改为您的HBase Master地址


    // 测试表配置
    private static final String TEST_TABLE_NAME = "hzz_woaini";
    private static final String COLUMN_FAMILY = "cf";
    private static final String NAME_COLUMN = "name";
    private static final String AGE_COLUMN = "age";
    private static final String EMAIL_COLUMN = "email";



    /**
     * 使用标准配置的HBase配置
     */
    private Configuration getHBaseConfig() {
        Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM);
        config.set("hbase.zookeeper.property.clientPort", String.valueOf(ZOOKEEPER_PORT));
        config.set("hbase.master", HBASE_MASTER);
        
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
     * 测试HBase连接
     */
    @Test
    public void testHBaseConnection() {
        System.out.println("=== 测试HBase连接 ===");
        
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Admin admin = connection.getAdmin()) {
                
                System.out.println("✅ HBase连接成功");
                
                TableName[] tableNames = admin.listTableNames();
                System.out.println("表数量: " + tableNames.length);
                
                for (TableName tableName : tableNames) {
                    System.out.println("  表: " + tableName.getNameAsString());
                }
                
            }
        } catch (Exception e) {
            System.out.println("❌ HBase连接失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 测试数据插入
     */
    @Test
    public void testInsertData() throws Exception {
        System.out.println("=== 测试数据插入 ===");
        
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                
                String rowKey = "user_" + RandomUtil.randomNumbers(10);
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("name"), Bytes.toBytes("张三"));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("age"), Bytes.toBytes(25));
                
                table.put(put);
                
                System.out.println("✅ 数据插入成功，rowKey: " + rowKey);
            }
        } catch (Exception e) {
            System.out.println("❌ 插入数据失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 测试数据查询
     */
    @Test
    public void testQueryData() throws Exception {
        System.out.println("=== 测试数据查询 ===");
        
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                
                Scan scan = new Scan();
                scan.setLimit(5);
                
                ResultScanner scanner = table.getScanner(scan);
                List<Map<String, Object>> records = new ArrayList<>();
                int count = 0;
                
                for (Result queryResult : scanner) {
                    count++;
                    String rowKey = Bytes.toString(queryResult.getRow());
                    Map<String, Object> record = new HashMap<>();
                    record.put("rowKey", rowKey);
                    records.add(record);
                }
                
                scanner.close();
                
                System.out.println("✅ 数据查询成功，查询到 " + count + " 条记录");
                for (Map<String, Object> record : records) {
                    System.out.println("  rowKey: " + record.get("rowKey"));
                }
                
            }
        } catch (Exception e) {
            System.out.println("❌ 查询数据失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 测试完整CRUD流程
     */
    @Test
    public void testFullCRUD() throws Exception {
        System.out.println("=== 测试完整CRUD流程 ===");
        
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                
                // 1. 插入数据
                String rowKey = "crud_test_" + System.currentTimeMillis();
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("name"), Bytes.toBytes("CRUD测试用户"));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("age"), Bytes.toBytes(28));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("createTime"), Bytes.toBytes(System.currentTimeMillis()));
                
                table.put(put);
                System.out.println("✅ 1. 数据插入成功，rowKey: " + rowKey);
                
                // 2. 查询刚插入的数据
                Get get = new Get(Bytes.toBytes(rowKey));
                Result result = table.get(get);
                
                if (!result.isEmpty()) {
                    String name = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("name")));
                    int age = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("age")));
                    System.out.println("✅ 2. 数据查询成功 - name: " + name + ", age: " + age);
                } else {
                    System.out.println("❌ 2. 数据查询失败，未找到数据");
                }
                
                // 3. 更新数据
                Put updatePut = new Put(Bytes.toBytes(rowKey));
                updatePut.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("age"), Bytes.toBytes(29));
                updatePut.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("updateTime"), Bytes.toBytes(System.currentTimeMillis()));
                
                table.put(updatePut);
                System.out.println("✅ 3. 数据更新成功");
                
                // 4. 验证更新
                Result updatedResult = table.get(get);
                if (!updatedResult.isEmpty()) {
                    int updatedAge = Bytes.toInt(updatedResult.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("age")));
                    System.out.println("✅ 4. 数据更新验证成功 - 新age: " + updatedAge);
                }
                
                // 5. 删除数据
                Delete delete = new Delete(Bytes.toBytes(rowKey));
                table.delete(delete);
                System.out.println("✅ 5. 数据删除成功");
                
                // 6. 验证删除
                Result deletedResult = table.get(get);
                if (deletedResult.isEmpty()) {
                    System.out.println("✅ 6. 数据删除验证成功");
                } else {
                    System.out.println("❌ 6. 数据删除验证失败，数据仍然存在");
                }
                
                System.out.println("🎉 完整CRUD流程测试完成！");
                
            }
        } catch (Exception e) {
            System.out.println("❌ CRUD流程测试失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 等待表上线
     */
    private void waitForTableOnline(Admin admin, String tableName) throws IOException {
        TableName tn = TableName.valueOf(tableName);
        int maxWait = 30; // 最多等待30秒
        
        for (int i = 0; i < maxWait; i++) {
            if (admin.isTableEnabled(tn)) {
                System.out.println("✅ 表 " + tableName + " 已上线");
                return;
            }
            System.out.println("⏳ 等待表 " + tableName + " 上线... (" + (i + 1) + "/" + maxWait + ")");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (!admin.isTableEnabled(tn)) {
            throw new IOException("表 " + tableName + " 上线超时");
        }
    }

    @Test
    public void testHBaseCRUD() throws IOException {
        System.out.println("=== 测试HBase CRUD操作 ===");
        
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
            System.out.println("❌ HBase CRUD测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        System.out.println("✅ HBase CRUD测试完成");
    }

    // ==================== 独立的CRUD测试方法 ====================

    /**
     * 独立测试：创建表
     */
    @Test
    public void testCreateTable() throws IOException {
        System.out.println("=== 独立测试：创建表 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            createTestTable(admin);
            waitForTableOnline(admin, TEST_TABLE_NAME);
            
            System.out.println("✅ 表创建测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 表创建测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：插入数据
     */
    @Test
    public void testInsertDataOnly() throws IOException {
        System.out.println("=== 独立测试：插入数据 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 确保表存在
            if (!admin.tableExists(TableName.valueOf(TEST_TABLE_NAME))) {
                createTestTable(admin);
                waitForTableOnline(admin, TEST_TABLE_NAME);
            }
            
            // 获取表对象并插入数据
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                testInsertData(table);
            }
            
            System.out.println("✅ 数据插入测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 数据插入测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：查询数据
     */
    @Test
    public void testQueryDataOnly() throws IOException {
        System.out.println("=== 独立测试：查询数据 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 确保表存在
            if (!admin.tableExists(TableName.valueOf(TEST_TABLE_NAME))) {
                System.out.println("⚠️ 测试表不存在，跳过查询测试");
                return;
            }
            
            // 获取表对象并查询数据
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                testQueryData(table);
            }
            
            System.out.println("✅ 数据查询测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 数据查询测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：更新数据
     */
    @Test
    public void testUpdateDataOnly() throws IOException {
        System.out.println("=== 独立测试：更新数据 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 确保表存在
            if (!admin.tableExists(TableName.valueOf(TEST_TABLE_NAME))) {
                System.out.println("⚠️ 测试表不存在，跳过更新测试");
                return;
            }
            
            // 获取表对象并更新数据
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                testUpdateData(table);
            }
            
            System.out.println("✅ 数据更新测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 数据更新测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：删除数据
     */
    @Test
    public void testDeleteDataOnly() throws IOException {
        System.out.println("=== 独立测试：删除数据 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 确保表存在
            if (!admin.tableExists(TableName.valueOf(TEST_TABLE_NAME))) {
                System.out.println("⚠️ 测试表不存在，跳过删除测试");
                return;
            }
            
            // 获取表对象并删除数据
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                testDeleteData(table);
            }
            
            System.out.println("✅ 数据删除测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 数据删除测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：批量操作
     */
    @Test
    public void testBatchOperationsOnly() throws IOException {
        System.out.println("=== 独立测试：批量操作 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 确保表存在
            if (!admin.tableExists(TableName.valueOf(TEST_TABLE_NAME))) {
                System.out.println("⚠️ 测试表不存在，跳过批量操作测试");
                return;
            }
            
            // 获取表对象并执行批量操作
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                testBatchOperations(table);
            }
            
            System.out.println("✅ 批量操作测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 批量操作测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：扫描操作
     */
    @Test
    public void testScanOperationsOnly() throws IOException {
        System.out.println("=== 独立测试：扫描操作 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 确保表存在
            if (!admin.tableExists(TableName.valueOf(TEST_TABLE_NAME))) {
                System.out.println("⚠️ 测试表不存在，跳过扫描测试");
                return;
            }
            
            // 获取表对象并执行扫描操作
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                testScanOperations(table);
            }
            
            System.out.println("✅ 扫描操作测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 扫描操作测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 独立测试：清理表
     */
    @Test
    public void testCleanupTable() throws IOException {
        System.out.println("=== 独立测试：清理表 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            cleanupTestTable(admin);
            
            System.out.println("✅ 表清理测试完成");
            
        } catch (Exception e) {
            System.out.println("❌ 表清理测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 创建测试表
     */
    private void createTestTable(Admin admin) throws IOException {
        TableName tableName = TableName.valueOf(TEST_TABLE_NAME);
        
        if (admin.tableExists(tableName)) {
            System.out.println("📋 测试表已存在: " + TEST_TABLE_NAME);
            // 确保表已启用
            if (!admin.isTableEnabled(tableName)) {
                System.out.println("🔄 启用已存在的表: " + TEST_TABLE_NAME);
                admin.enableTable(tableName);
            }
            return;
        }
        
        System.out.println("🏗️ 开始创建测试表: " + TEST_TABLE_NAME);
        TableDescriptorBuilder tableDescriptorBuilder = TableDescriptorBuilder.newBuilder(tableName);
        ColumnFamilyDescriptor columnFamilyDescriptor = ColumnFamilyDescriptorBuilder
                .newBuilder(Bytes.toBytes(COLUMN_FAMILY))
                .build();
        
        tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
        admin.createTable(tableDescriptorBuilder.build());
        System.out.println("✅ 测试表创建成功: " + TEST_TABLE_NAME);
        
        // 等待表创建完成
        try {
            Thread.sleep(2000); // 等待2秒让表完全创建
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("⚠️ 等待表创建完成被中断");
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
//            System.out.println("🧹 测试表已删除: " + TEST_TABLE_NAME);
//        }
    }

    /**
     * 测试插入数据
     */
    private void testInsertData(Table table) throws IOException {
        System.out.println("--- 测试插入数据 ---");
        
        String rowKey = "user_002"+ RandomUtil.randomNumbers(10);
        String name = "张三";
        int age = 25;
        String email = "zhangsan@example.com";
        
        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN), Bytes.toBytes(name));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN), Bytes.toBytes(age));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN), Bytes.toBytes(email));
        
        table.put(put);
        System.out.println("✅ 插入数据成功: rowKey=" + rowKey + ", name=" + name + ", age=" + age + ", email=" + email);
    }

    /**
     * 测试查询数据
     */
    private void testQueryData(Table table) throws IOException {
        System.out.println("--- 测试查询数据 ---");
        
        String rowKey = "user_001";
        Get get = new Get(Bytes.toBytes(rowKey));
        Result result = table.get(get);
        
        if (!result.isEmpty()) {
            String name = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN)));
            int age = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN)));
            String email = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN)));
            
            System.out.println("📖 查询结果: rowKey=" + rowKey + ", name=" + name + ", age=" + age + ", email=" + email);
        } else {
            System.out.println("⚠️ 未找到数据: rowKey=" + rowKey);
        }
    }

    /**
     * 测试更新数据
     */
    private void testUpdateData(Table table) throws IOException {
        System.out.println("--- 测试更新数据 ---");
        
        String rowKey = "user_001";
        String newName = "张三(已更新)";
        int newAge = 26;
        
        Put put = new Put(Bytes.toBytes(rowKey));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN), Bytes.toBytes(newName));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN), Bytes.toBytes(newAge));
        
        table.put(put);
        System.out.println("✅ 更新数据成功: rowKey=" + rowKey + ", newName=" + newName + ", newAge=" + newAge);
        
        // 验证更新结果
        Get get = new Get(Bytes.toBytes(rowKey));
        Result result = table.get(get);
        String updatedName = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN)));
        int updatedAge = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN)));
        
        System.out.println("🔍 更新验证: name=" + updatedName + ", age=" + updatedAge);
    }

    /**
     * 测试删除数据
     */
    private void testDeleteData(Table table) throws IOException {
        System.out.println("--- 测试删除数据 ---");
        
        String rowKey = "user_001";
        
        // 删除整行数据
        Delete delete = new Delete(Bytes.toBytes(rowKey));
        table.delete(delete);
        System.out.println("🗑️ 删除数据成功: rowKey=" + rowKey);
        
        // 验证删除结果
        Get get = new Get(Bytes.toBytes(rowKey));
        Result result = table.get(get);
        
        if (result.isEmpty()) {
            System.out.println("✅ 数据删除验证成功: rowKey=" + rowKey);
        } else {
            System.out.println("⚠️ 数据删除验证失败: rowKey=" + rowKey);
        }
    }

    /**
     * 测试批量操作
     */
    private void testBatchOperations(Table table) throws IOException {
        System.out.println("--- 测试批量操作 ---");
        
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
            System.out.println("✅ 批量插入成功: " + puts.size() + "条数据");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("❌ 批量插入被中断: " + e.getMessage());
            e.printStackTrace();
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
        
        System.out.println("📊 批量插入验证: 成功" + successCount + "条");
    }

    /**
     * 测试扫描操作
     */
    private void testScanOperations(Table table) throws IOException {
        System.out.println("--- 测试扫描操作 ---");
        
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
            
            System.out.println("📖 扫描数据 " + count + ": rowKey=" + rowKey + ", name=" + name + ", age=" + age);
        }
        
        scanner.close();
        System.out.println("📊 扫描完成: 共" + count + "条数据");
    }

    /**
     * 检查并修复表状态
     */
    private void checkAndFixTableStatus(Admin admin, String tableName) throws IOException {
        TableName tn = TableName.valueOf(tableName);
        
        if (!admin.tableExists(tn)) {
            System.out.println(" 表 " + tableName + " 不存在，跳过检查");
            return;
        }
        
        // 检查表是否启用
        if (!admin.isTableEnabled(tn)) {
            System.out.println(" 表 " + tableName + " 已禁用，正在启用...");
            admin.enableTable(tn);
            
            // 等待表启用
            waitForTableOnline(admin, tableName);
        }
        
        // 获取表的Region信息
        List<RegionInfo> regions = admin.getRegions(tn);
        System.out.println("📊 表 " + tableName + " 有 " + regions.size() + " 个Region");
        
        for (RegionInfo region : regions) {
            System.out.println("  - Region: " + region.getRegionNameAsString());
        }
    }

    @Test
    public void checkAllTablesStatus() throws IOException {
        System.out.println("=== 检查所有表状态 ===");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            // 获取所有表
            TableName[] tableNames = admin.listTableNames();
            System.out.println("📋 发现 " + tableNames.length + " 个表");
            
            for (TableName tableName : tableNames) {
                String tableNameStr = tableName.getNameAsString();
                System.out.println("🔍 检查表: " + tableNameStr);
                
                try {
                    checkAndFixTableStatus(admin, tableNameStr);
                } catch (Exception e) {
                    System.out.println("⚠️ 检查表 " + tableNameStr + " 状态时出错: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.out.println("❌ 检查表状态失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 测试执行顺序建议
     */
    @Test
    public void testExecutionOrder() throws IOException {
        System.out.println("=== 测试执行顺序建议 ===");
        System.out.println("📋 建议按以下顺序执行测试：");
        System.out.println("");
        System.out.println("1️⃣ 基础连接测试：");
        System.out.println("   - testHBaseConnection()");
        System.out.println("");
        System.out.println("2️⃣ 表操作测试：");
        System.out.println("   - testCreateTable()");
        System.out.println("");
        System.out.println("3️⃣ 数据操作测试（按顺序）：");
        System.out.println("   - testInsertDataOnly()");
        System.out.println("   - testQueryDataOnly()");
        System.out.println("   - testUpdateDataOnly()");
        System.out.println("   - testQueryDataOnly()  // 验证更新");
        System.out.println("   - testDeleteDataOnly()");
        System.out.println("   - testQueryDataOnly()  // 验证删除");
        System.out.println("");
        System.out.println("4️⃣ 高级操作测试：");
        System.out.println("   - testBatchOperationsOnly()");
        System.out.println("   - testScanOperationsOnly()");
        System.out.println("");
        System.out.println("5️⃣ 清理测试：");
        System.out.println("   - testCleanupTable()");
        System.out.println("");
        System.out.println("🎯 或者直接运行完整测试：");
        System.out.println("   - testHBaseCRUD()");
        System.out.println("");
        System.out.println("💡 如果某个步骤失败，可以单独重试该步骤");
        System.out.println("💡 每个独立测试都会检查表是否存在，不存在会自动创建");
        
        // 这里不执行任何实际操作，只是提供建议
        System.out.println("✅ 测试执行顺序建议完成");
    }
} 