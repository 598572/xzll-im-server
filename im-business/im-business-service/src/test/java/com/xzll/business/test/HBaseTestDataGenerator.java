package com.xzll.business.test;

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
import java.util.Random;

/**
 * @Author: hzz
 * @Date: 2024/6/16 14:08:54
 * @Description: 用于批量生成大量测试数据，测试系统性能
 */
@Slf4j
public class HBaseTestDataGenerator {

    // ==================== 配置参数 ====================
    // 请根据您的实际环境修改这些参数
    private static final String ZOOKEEPER_QUORUM = "120.46.85.43";  // 修改为您的ZooKeeper地址
    private static final int ZOOKEEPER_PORT = 2181;              // 修改为您的ZooKeeper端口
    private static final String HBASE_MASTER = "120.46.85.43"; // 修改为您的HBase Master地址

    private static final String PUBLIC_HOST = "120.46.85.43"; // 修改为您的HBase Master地址


    // 测试表配置
    private static final String TEST_TABLE_NAME = "test_user_info3";
    private static final String COLUMN_FAMILY = "cf";
    private static final String NAME_COLUMN = "name";
    private static final String AGE_COLUMN = "age";
    private static final String EMAIL_COLUMN = "email";
    private static final String PHONE_COLUMN = "phone";
    private static final String ADDRESS_COLUMN = "address";
    private static final String COMPANY_COLUMN = "company";
    private static final String POSITION_COLUMN = "position";
    private static final String SALARY_COLUMN = "salary";
    private static final String CREATE_TIME_COLUMN = "create_time";

    // 数据生成配置
    private static final int TOTAL_RECORDS = 100;  // 总记录数
    private static final int BATCH_SIZE = 10;      // 批量插入大小

    // 随机数据生成器
    private static final Random random = new Random();

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
        config.set("hbase.client.discovery.enabled", "false");
        config.set("hbase.master.dns.interface", "default");
        config.set("hbase.regionserver.dns.interface", "default");
        
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
        config.set("hbase.client.dns.interface", "default");
        config.set("hbase.client.dns.nameserver", "8.8.8.8");


        // 直接指定所有 RegionServer 地址（通过 FRP 映射的地址）
        config.set("hbase.client.ipc.pool.type", "RoundRobinPool");
        config.set("hbase.client.ipc.pool.size", "3");
        config.set("hbase.regionserver.port", "16020"); // 基础端口

// 设置所有 RegionServer 地址
        config.set("hbase.regionserver.address.0", PUBLIC_HOST+":16020"); // hadoop01
        config.set("hbase.regionserver.address.1", PUBLIC_HOST+":16021"); // hadoop02
        config.set("hbase.regionserver.address.2", PUBLIC_HOST+":16022"); // hadoop03
        
        log.info("HBase配置: ZooKeeper={}:{}, Master={}", ZOOKEEPER_QUORUM, ZOOKEEPER_PORT, HBASE_MASTER);
        return config;
    }

    /**
     * 测试连接并生成1000条测试数据
     */
    @Test
    public void generateTestData() throws IOException {
        log.info("=== 开始生成{}条测试数据 ===", TOTAL_RECORDS);
        
        // 设置系统属性来覆盖HBase内部超时
        System.setProperty("hbase.client.operation.timeout", "60000");
        System.setProperty("hbase.client.scanner.timeout.period", "60000");
        System.setProperty("hbase.client.retries.number", "5");
        System.setProperty("hbase.client.pause", "2000");
        System.setProperty("zookeeper.session.timeout", "60000");
        System.setProperty("zookeeper.recovery.retry", "5");
        System.setProperty("zookeeper.recovery.retry.intervalmill", "2000");
        System.setProperty("zookeeper.request.timeout", "60000");
        
        Configuration config = getHBaseConfig();
        try (Connection connection = ConnectionFactory.createConnection(config);
             Admin admin = connection.getAdmin()) {
            
            log.info("✅ HBase连接成功");
            
            // 创建测试表
            createTestTable(admin);
            
            // 获取表对象并生成数据
            try (Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                
                // 生成测试数据
                generateBulkTestData(table);
                
                // 验证数据生成结果
                verifyDataGeneration(table);
                
            }
            
        } catch (Exception e) {
            log.error("❌ 测试数据生成失败", e);
            throw e;
        }
        
        log.info("✅ 测试数据生成完成");
    }

    /**
     * 创建测试表
     */
    private void createTestTable(Admin admin) throws IOException {
        TableName tableName = TableName.valueOf(TEST_TABLE_NAME);
        
        if (admin.tableExists(tableName)) {
            log.info("📋 测试表已存在: {}", TEST_TABLE_NAME);
            return;
        }
        
        TableDescriptorBuilder tableDescriptorBuilder = TableDescriptorBuilder.newBuilder(tableName);
        ColumnFamilyDescriptor columnFamilyDescriptor = ColumnFamilyDescriptorBuilder
                .newBuilder(Bytes.toBytes(COLUMN_FAMILY))
                .build();
        
        tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
        admin.createTable(tableDescriptorBuilder.build());
        log.info("✅ 测试表创建成功: {}", TEST_TABLE_NAME);
    }

    /**
     * 批量生成测试数据
     */
    private void generateBulkTestData(Table table) throws IOException {
        log.info("--- 开始批量生成{}条测试数据 ---", TOTAL_RECORDS);
        
        long startTime = System.currentTimeMillis();
        int totalInserted = 0;
        
        // 分批插入数据
        for (int batchStart = 0; batchStart < TOTAL_RECORDS; batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, TOTAL_RECORDS);
            int batchSize = batchEnd - batchStart;
            
            List<Put> puts = new ArrayList<>();
            
            // 准备当前批次的数据
            for (int i = batchStart; i < batchEnd; i++) {
                Put put = createTestRecord(i + 1);
                puts.add(put);
            }
            
            // 批量插入当前批次
            try {
                Object[] results = new Object[puts.size()];
                table.batch(puts, results);
                totalInserted += puts.size();
                
                log.info("✅ 批次插入成功: {}-{} (共{}条)", batchStart + 1, batchEnd, puts.size());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("❌ 批次插入被中断: {}-{}", batchStart + 1, batchEnd, e);
                break;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("📊 数据生成完成: 成功插入{}条，耗时{}ms，平均{}ms/条", 
                totalInserted, duration, totalInserted > 0 ? duration / totalInserted : 0);
    }

    /**
     * 创建单条测试记录
     */
    private Put createTestRecord(int recordIndex) {
        String rowKey = "user_" + String.format("%06d", recordIndex);
        
        Put put = new Put(Bytes.toBytes(rowKey));
        
        // 生成随机测试数据
        String name = generateRandomName();
        int age = 18 + random.nextInt(62); // 18-79岁
        String email = generateRandomEmail(name);
        String phone = generateRandomPhone();
        String address = generateRandomAddress();
        String company = generateRandomCompany();
        String position = generateRandomPosition();
        int salary = 3000 + random.nextInt(50000); // 3000-53000
        long createTime = System.currentTimeMillis() - random.nextInt(365 * 24 * 60 * 60 * 1000); // 随机创建时间
        
        // 添加列数据
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN), Bytes.toBytes(name));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN), Bytes.toBytes(age));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN), Bytes.toBytes(email));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(PHONE_COLUMN), Bytes.toBytes(phone));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(ADDRESS_COLUMN), Bytes.toBytes(address));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COMPANY_COLUMN), Bytes.toBytes(company));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(POSITION_COLUMN), Bytes.toBytes(position));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(SALARY_COLUMN), Bytes.toBytes(salary));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(CREATE_TIME_COLUMN), Bytes.toBytes(createTime));
        
        return put;
    }

    /**
     * 验证数据生成结果
     */
    private void verifyDataGeneration(Table table) throws IOException {
        log.info("--- 验证数据生成结果 ---");
        
        // 扫描表数据统计总数
        Scan scan = new Scan();
        ResultScanner scanner = table.getScanner(scan);
        int count = 0;
        
        for (Result result : scanner) {
            count++;
            if (count <= 5) { // 只显示前5条数据作为示例
                String rowKey = Bytes.toString(result.getRow());
                String name = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(NAME_COLUMN)));
                int age = Bytes.toInt(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(AGE_COLUMN)));
                String email = Bytes.toString(result.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(EMAIL_COLUMN)));
                
                log.info("📖 示例数据 {}: rowKey={}, name={}, age={}, email={}", count, rowKey, name, age, email);
            }
        }
        
        scanner.close();
        log.info("📊 数据验证完成: 表中实际记录数{}条", count);
        
        if (count >= TOTAL_RECORDS) {
            log.info("✅ 数据生成验证成功: 目标{}条，实际{}条", TOTAL_RECORDS, count);
        } else {
            log.warn("⚠️ 数据生成验证警告: 目标{}条，实际{}条", TOTAL_RECORDS, count);
        }
    }

    // ==================== 随机数据生成方法 ====================
    
    private String generateRandomName() {
        String[] surnames = {"张", "王", "李", "赵", "陈", "刘", "杨", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗"};
        String[] names = {"伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "秀兰", "霞", "平", "刚", "桂英"};
        
        return surnames[random.nextInt(surnames.length)] + names[random.nextInt(names.length)];
    }
    
    private String generateRandomEmail(String name) {
        String[] domains = {"gmail.com", "163.com", "qq.com", "126.com", "sina.com", "hotmail.com", "yahoo.com"};
        return name.toLowerCase() + random.nextInt(9999) + "@" + domains[random.nextInt(domains.length)];
    }
    
    private String generateRandomPhone() {
        String[] prefixes = {"130", "131", "132", "133", "134", "135", "136", "137", "138", "139", "150", "151", "152", "153", "155", "156", "157", "158", "159", "180", "181", "182", "183", "184", "185", "186", "187", "188", "189"};
        return prefixes[random.nextInt(prefixes.length)] + String.format("%08d", random.nextInt(100000000));
    }
    
    private String generateRandomAddress() {
        String[] cities = {"北京市", "上海市", "广州市", "深圳市", "杭州市", "南京市", "成都市", "武汉市", "西安市", "重庆市"};
        String[] districts = {"朝阳区", "海淀区", "西城区", "东城区", "丰台区", "石景山区", "门头沟区", "房山区", "通州区", "顺义区"};
        String[] streets = {"中关村大街", "建国门外大街", "长安街", "王府井大街", "西单北大街", "东单北大街", "复兴门外大街", "阜成门外大街", "西直门外大街", "德胜门外大街"};
        
        return cities[random.nextInt(cities.length)] + districts[random.nextInt(districts.length)] + streets[random.nextInt(streets.length)] + random.nextInt(999) + "号";
    }
    
    private String generateRandomCompany() {
        String[] companies = {"腾讯科技", "阿里巴巴", "百度在线", "字节跳动", "美团点评", "滴滴出行", "京东集团", "网易公司", "小米科技", "华为技术", "联想集团", "中兴通讯", "中国移动", "中国联通", "中国电信"};
        return companies[random.nextInt(companies.length)];
    }
    
    private String generateRandomPosition() {
        String[] positions = {"软件工程师", "产品经理", "UI设计师", "测试工程师", "运维工程师", "数据分析师", "算法工程师", "前端工程师", "后端工程师", "全栈工程师", "项目经理", "技术总监", "架构师", "CTO", "CEO"};
        return positions[random.nextInt(positions.length)];
    }
} 