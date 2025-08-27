package com.xzll.business.controller;

import cn.hutool.core.util.RandomUtil;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HBase测试Controller
 */
@RestController
@RequestMapping("/hbase/test")
@CrossOrigin
public class HBaseTestController {

    private static final String ZOOKEEPER_QUORUM = "120.46.85.43";
    private static final int ZOOKEEPER_PORT = 2181;
    private static final String HBASE_MASTER = "120.46.85.43";
    private static final String TEST_TABLE_NAME = "hzz_woaini";
    private static final String COLUMN_FAMILY = "cf";

    private Configuration getHBaseConfig() {
        Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM);
        config.set("hbase.zookeeper.property.clientPort", String.valueOf(ZOOKEEPER_PORT));
        config.set("hbase.master", HBASE_MASTER);
        
        // 明确指定RegionServer地址 - 解决架构问题
//        config.set("hbase.regionserver.hostname.0", "hadoop02");
//        config.set("hbase.regionserver.hostname.1", "hadoop03");
//        config.set("hbase.regionserver.port", "16020");
        
        // 禁用HBase Master作为RegionServer
        config.set("hbase.master.dns.interface", "default");
        config.set("hbase.master.regionserver.port", "16000");
        
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
        
        // 强制使用指定的RegionServer
//        config.set("hbase.client.connection.impl", "org.apache.hadoop.hbase.client.ConnectionImplementation");
        config.set("hbase.client.async.operation.timeout", "30000");
        
        return config;
    }

    @GetMapping("/connection")
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Admin admin = connection.getAdmin()) {
                
                result.put("success", true);
                result.put("message", "HBase连接成功");
                
                TableName[] tableNames = admin.listTableNames();
                result.put("tableCount", tableNames.length);
                
                List<String> tables = new ArrayList<>();
                for (TableName tableName : tableNames) {
                    tables.add(tableName.getNameAsString());
                }
                result.put("tables", tables);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "HBase连接失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/data/insert")
    public Map<String, Object> insertData() {
        Map<String, Object> result = new HashMap<>();
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Table table = connection.getTable(TableName.valueOf(TEST_TABLE_NAME))) {
                
                String rowKey = "user_" + RandomUtil.randomNumbers(10);
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("name"), Bytes.toBytes("张三"));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("age"), Bytes.toBytes(25));
                
                table.put(put);
                
                result.put("success", true);
                result.put("message", "数据插入成功");
                result.put("rowKey", rowKey);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "插入数据失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/data/scan")
    public Map<String, Object> scanData() {
        Map<String, Object> result = new HashMap<>();
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
                
                result.put("success", true);
                result.put("message", "数据扫描成功");
                result.put("count", count);
                result.put("records", records);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "扫描数据失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 诊断Region问题
     */
    @GetMapping("/diagnose")
    public Map<String, Object> diagnoseRegionIssue() {
        Map<String, Object> result = new HashMap<>();
        try {
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config);
                 Admin admin = connection.getAdmin()) {
                
                result.put("success", true);
                result.put("message", "开始诊断Region问题");
                
                // 检查测试表状态
                TableName testTableName = TableName.valueOf(TEST_TABLE_NAME);
                if (admin.tableExists(testTableName)) {
                    result.put("tableExists", true);
                    result.put("tableEnabled", admin.isTableEnabled(testTableName));
                    
                    // 获取Region信息
                    List<RegionInfo> regions = admin.getRegions(testTableName);
                    result.put("regionCount", regions.size());
                    
                    List<Map<String, Object>> regionInfo = new ArrayList<>();
                    for (RegionInfo region : regions) {
                        Map<String, Object> regionData = new HashMap<>();
                        regionData.put("regionName", region.getRegionNameAsString());
                        regionData.put("regionId", region.getRegionId());
                        regionData.put("tableName", region.getTable().getNameAsString());
                        regionInfo.add(regionData);
                    }
                    result.put("regions", regionInfo);
                    
                    // 诊断建议
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("1. 在HBase Shell中执行: balance_switch false");
                    suggestions.add("2. 手动移动Region到稳定位置");
                    suggestions.add("3. 检查RegionServer状态");
                    suggestions.add("4. 重启HBase集群");
                    result.put("suggestions", suggestions);
                    
                } else {
                    result.put("tableExists", false);
                    result.put("message", "测试表不存在，需要先创建表");
                }
                
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "诊断失败: " + e.getMessage());
            result.put("error", e.toString());
        }
        return result;
    }

    /**
     * 强制刷新连接 - 创建全新连接
     */
    @PostMapping("/refresh")
    public Map<String, Object> refreshConnection() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 强制GC清理内存
            System.gc();
            
            Configuration config = getHBaseConfig();
            try (Connection connection = ConnectionFactory.createConnection(config)) {
                
                result.put("success", true);
                result.put("message", "连接已刷新");
                result.put("timestamp", System.currentTimeMillis());
                
                // 测试连接
                try (Admin admin = connection.getAdmin()) {
                    TableName[] tableNames = admin.listTableNames();
                    result.put("tableCount", tableNames.length);
                }
                
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "刷新连接失败: " + e.getMessage());
        }
        return result;
    }





    @Autowired
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;

    /**
     * 发送C2C消息
     * @param sendMsgAO 消息请求对象
     * @return 发送结果
     */
    @PostMapping("/send")
    public void sendMessage(@Valid @RequestBody C2CSendMsgAO sendMsgAO) {
        imC2CMsgRecordHBaseService.saveC2CMsg(sendMsgAO);
    }


} 