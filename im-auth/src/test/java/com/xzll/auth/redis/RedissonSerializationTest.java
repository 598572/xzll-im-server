package com.xzll.auth.redis;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redisson序列化测试类
 * 专门测试Redisson存储和读取时的二进制问题
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
//@TestPropertySource(properties = {
//    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
//})
@Slf4j
public class RedissonSerializationTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void testRedissonStringOperations() {
        log.info("=== 测试Redisson String操作 ===");
        
        String testValue = "test_value_123";
        String testKey = "test:redisson:string";
        
        try {
            RBucket<String> bucket = redissonClient.getBucket(testKey);
            
            // 存储
            bucket.set(testValue, 60, TimeUnit.SECONDS);
            log.info("✅ 存储成功: key={}, value={}", testKey, testValue);
            
            // 读取
            String retrievedValue = bucket.get();
            log.info("📖 读取结果: key={}, retrievedValue={}", testKey, retrievedValue);
            
            // 检查是否相等
            boolean isEqual = testValue.equals(retrievedValue);
            log.info("🔍 值是否相等: {}", isEqual);
            
            // 详细分析
            analyzeBinaryIssue(testValue, retrievedValue, "Redisson String");
            
            // 清理
            bucket.delete();
            log.info("🧹 清理完成: {}", testKey);
            
        } catch (Exception e) {
            log.error("❌ 测试异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testRedissonHashOperations() {
        log.info("=== 测试Redisson Hash操作 ===");
        
        String hashKey = "test:redisson:hash";
        String field1 = "name";
        String field2 = "age";
        String field3 = "email";
        
        String value1 = "张三";
        String value2 = "25";
        String value3 = "zhangsan@example.com";
        
        try {
            RMap<String, String> map = redissonClient.getMap(hashKey);
            
            // 存储
            map.put(field1, value1);
            map.put(field2, value2);
            map.put(field3, value3);
            map.expire(60, TimeUnit.SECONDS);
            
            log.info("✅ 存储成功: key={}", hashKey);
            
            // 读取
            String retrievedValue1 = map.get(field1);
            String retrievedValue2 = map.get(field2);
            String retrievedValue3 = map.get(field3);
            
            log.info("Redisson Hash结果:");
            log.info("  field1: 原始值='{}', 读取值='{}', 是否相等={}", field1, value1, retrievedValue1, value1.equals(retrievedValue1));
            log.info("  field2: 原始值='{}', 读取值='{}', 是否相等={}", field2, value2, retrievedValue2, value2.equals(retrievedValue2));
            log.info("  field3: 原始值='{}', 读取值='{}', 是否相等={}", field3, value3, retrievedValue3, value3.equals(retrievedValue3));
            
            // 检查二进制问题
            if (!value1.equals(retrievedValue1)) {
                analyzeBinaryIssue(value1, retrievedValue1, "Redisson Hash field1");
            }
            if (!value2.equals(retrievedValue2)) {
                analyzeBinaryIssue(value2, retrievedValue2, "Redisson Hash field2");
            }
            if (!value3.equals(retrievedValue3)) {
                analyzeBinaryIssue(value3, retrievedValue3, "Redisson Hash field3");
            }
            
            // 清理
            map.delete();
            log.info("🧹 清理完成: {}", hashKey);
            
        } catch (Exception e) {
            log.error("❌ 测试异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testRedissonDifferentDataTypes() {
        log.info("=== 测试Redisson不同数据类型 ===");
        
        // 测试数字
        testRedissonDataType("123456", "test:redisson:number");
        
        // 测试特殊字符
        testRedissonDataType("test@#$%^&*()_+-=[]{}|;':\",./<>?", "test:redisson:special");
        
        // 测试中文
        testRedissonDataType("测试中文内容", "test:redisson:chinese");
        
        // 测试长字符串
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longString.append("这是第").append(i).append("个字符，");
        }
        testRedissonDataType(longString.toString(), "test:redisson:long");
    }

    @Test
    public void testRedissonHashBatchOperations() {
        log.info("=== 测试Redisson Hash批量操作 ===");
        
        String hashKey = "test:redisson:hash:batch";
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("field1", "value1");
        dataMap.put("field2", "value2");
        dataMap.put("field3", "value3");
        dataMap.put("field4", "测试中文");
        dataMap.put("field5", "special@#$%");
        
        try {
            RMap<String, String> map = redissonClient.getMap(hashKey);
            
            // 批量存储
            map.putAll(dataMap);
            map.expire(60, TimeUnit.SECONDS);
            
            log.info("✅ 批量存储成功: key={}", hashKey);
            
            // 批量读取
            Map<String, String> retrievedMap = map.readAllMap();
            
            log.info("Redisson批量操作结果:");
            for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                String originalValue = entry.getValue();
                String retrievedValue = retrievedMap.get(entry.getKey());
                boolean isEqual = originalValue.equals(retrievedValue);
                log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", 
                        entry.getKey(), originalValue, retrievedValue, isEqual);
                
                if (!isEqual) {
                    analyzeBinaryIssue(originalValue, retrievedValue, "Redisson Hash批量 " + entry.getKey());
                }
            }
            
            // 清理
            map.delete();
            log.info("🧹 清理完成: {}", hashKey);
            
        } catch (Exception e) {
            log.error("❌ 批量操作异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testRedissonByteAnalysis() {
        log.info("=== Redisson字节级别分析 ===");
        
        String testValue = "test_value_789";
        String testKey = "test:redisson:byte:analysis";
        
        try {
            RBucket<String> bucket = redissonClient.getBucket(testKey);
            bucket.set(testValue, 60, TimeUnit.SECONDS);
            String retrievedValue = bucket.get();
            
            // 字节分析
            byte[] originalBytes = testValue.getBytes(StandardCharsets.UTF_8);
            byte[] retrievedBytes = retrievedValue.getBytes(StandardCharsets.UTF_8);
            
            log.info("原始值字节: {}", Arrays.toString(originalBytes));
            log.info("读取值字节: {}", Arrays.toString(retrievedBytes));
            log.info("原始值字节长度: {}", originalBytes.length);
            log.info("读取值字节长度: {}", retrievedBytes.length);
            
            // 查找null字符
            int nullCount = 0;
            for (byte b : retrievedBytes) {
                if (b == 0) {
                    nullCount++;
                }
            }
            log.info("读取值中null字符数量: {}", nullCount);
            
            // 清理
            bucket.delete();
            
        } catch (Exception e) {
            log.error("❌ 字节分析异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testRedissonMultipleOperations() {
        log.info("=== Redisson多次操作测试 ===");
        
        String testKey = "test:redisson:multiple:operations";
        
        for (int i = 1; i <= 5; i++) {
            String testValue = "test_value_" + i;
            
            try {
                RBucket<String> bucket = redissonClient.getBucket(testKey);
                bucket.set(testValue, 60, TimeUnit.SECONDS);
                String retrievedValue = bucket.get();
                
                boolean isEqual = testValue.equals(retrievedValue);
                log.info("第{}次操作: 原始值='{}', 读取值='{}', 是否相等={}", 
                        i, testValue, retrievedValue, isEqual);
                
                if (!isEqual) {
                    analyzeBinaryIssue(testValue, retrievedValue, "第" + i + "次操作");
                }
                
            } catch (Exception e) {
                log.error("❌ 第{}次操作异常: {}", i, e.getMessage());
            }
        }
        
        // 清理
        redissonClient.getBucket(testKey).delete();
    }

    private void testRedissonDataType(String testValue, String testKey) {
        log.info("--- 测试数据类型: {} ---", testKey);
        
        try {
            RBucket<String> bucket = redissonClient.getBucket(testKey);
            bucket.set(testValue, 60, TimeUnit.SECONDS);
            String retrievedValue = bucket.get();
            
            // 检查是否相等
            boolean isEqual = testValue.equals(retrievedValue);
            log.info("数据类型: {}, 原始值: '{}', 读取值: '{}', 是否相等: {}", 
                    testKey, testValue, retrievedValue, isEqual);
            
            if (!isEqual) {
                analyzeBinaryIssue(testValue, retrievedValue, testKey);
            }
            
            // 清理
            bucket.delete();
            
        } catch (Exception e) {
            log.error("❌ 数据类型测试异常 {}: {}", testKey, e.getMessage());
        }
    }

    private void analyzeBinaryIssue(String originalValue, String retrievedValue, String context) {
        log.info("🔍 二进制问题分析 - {}", context);
        
        if (originalValue == null || retrievedValue == null) {
            log.warn("⚠️ 值为null，跳过分析");
            return;
        }
        
        // 检查长度
        log.info("  原始值长度: {}", originalValue.length());
        log.info("  读取值长度: {}", retrievedValue.length());
        
        // 检查是否包含null字符
        boolean hasNullChars = retrievedValue.contains("\u0000");
        log.info("  是否包含null字符: {}", hasNullChars);
        
        if (hasNullChars) {
            // 计算null字符数量
            int nullCount = 0;
            for (char c : retrievedValue.toCharArray()) {
                if (c == '\u0000') {
                    nullCount++;
                }
            }
            log.info("  null字符数量: {}", nullCount);
            
            // 找到第一个非null字符的位置
            int firstNonNullIndex = -1;
            for (int i = 0; i < retrievedValue.length(); i++) {
                if (retrievedValue.charAt(i) != '\u0000') {
                    firstNonNullIndex = i;
                    break;
                }
            }
            log.info("  第一个非null字符位置: {}", firstNonNullIndex);
            
            // 提取有效内容
            if (firstNonNullIndex >= 0) {
                String validContent = retrievedValue.substring(firstNonNullIndex);
                log.info("  有效内容: '{}'", validContent);
                log.info("  有效内容是否与原始值相等: {}", originalValue.equals(validContent));
            }
        }
        
        // 字节分析
        byte[] originalBytes = originalValue.getBytes(StandardCharsets.UTF_8);
        byte[] retrievedBytes = retrievedValue.getBytes(StandardCharsets.UTF_8);
        
        log.info("  原始值字节长度: {}", originalBytes.length);
        log.info("  读取值字节长度: {}", retrievedBytes.length);
        
        // 检查前几个字节
        int checkLength = Math.min(10, retrievedBytes.length);
        log.info("  读取值前{}个字节: {}", checkLength, 
                Arrays.toString(Arrays.copyOf(retrievedBytes, checkLength)));
    }
} 