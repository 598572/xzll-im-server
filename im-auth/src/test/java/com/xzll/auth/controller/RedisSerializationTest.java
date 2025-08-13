package com.xzll.auth.controller;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@ComponentScan(basePackages = "com.xzll.common.config", 
               excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, 
               classes = {com.xzll.auth.controller.AuthController.class}))
@Slf4j
public class RedisSerializationTest {

    @Autowired
    @Qualifier("myStringRedisTemplate")
    private RedisTemplate<String, String> myStringRedisTemplate;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testAllRedisTemplates() {
        log.info("=== 对比测试所有RedisTemplate ===");
        
        String testValue = "test_value_123";
        String testKey = "testserialization:";
        
        testSingleTemplate("myStringRedisTemplate", myStringRedisTemplate, testKey + "1", testValue);
        testSingleTemplate("redisTemplate", redisTemplate, testKey + "2", testValue);
        testSingleTemplate("stringRedisTemplate", stringRedisTemplate, testKey + "3", testValue);
    }

    @Test
    public void testSpecialCharacters() {
        log.info("=== 测试特殊字符存储 ===");
        
        String specialValue = "test@#$%^&*()_+-=[]{}|;':\",./<>?中文测试";
        String testKey = "test:special:";
        
        testSingleTemplate("myStringRedisTemplate", myStringRedisTemplate, testKey + "1", specialValue);
        testSingleTemplate("redisTemplate", redisTemplate, testKey + "2", specialValue);
        testSingleTemplate("stringRedisTemplate", stringRedisTemplate, testKey + "3", specialValue);
    }

    @Test
    public void testNumberStorage() {
        log.info("=== 测试数字存储 ===");
        
        String numberValue = "123456789";
        String testKey = "test:number:";
        
        testSingleTemplate("myStringRedisTemplate", myStringRedisTemplate, testKey + "1", numberValue);
        testSingleTemplate("redisTemplate", redisTemplate, testKey + "2", numberValue);
        testSingleTemplate("stringRedisTemplate", stringRedisTemplate, testKey + "3", numberValue);
    }

    private void testSingleTemplate(String templateName, RedisTemplate<String, String> template, String testKey, String testValue) {
        log.info("测试 {}: key={}, value={}", templateName, testKey, testValue);
        
        try {
            template.opsForValue().set(testKey, testValue, 60);
            String retrievedValue = (String) template.opsForValue().get(testKey);
            boolean isEqual = testValue.equals(retrievedValue);
            
            if (isEqual) {
                log.info("✅ {}: 测试通过", templateName);
            } else {
                log.error("❌ {}: 测试失败", templateName);
                log.error("  原始值: '{}'", testValue);
                log.error("  读取值: '{}'", retrievedValue);
                log.error("  原始值字节: {}", Arrays.toString(testValue.getBytes(StandardCharsets.UTF_8)));
                log.error("  读取值字节: {}", Arrays.toString(retrievedValue.getBytes(StandardCharsets.UTF_8)));
            }
            
            template.delete(testKey);
            
        } catch (Exception e) {
            log.error("❌ {}: 测试异常 - {}", templateName, e.getMessage(), e);
        }
    }

    @Test
    public void testSerializerConfiguration() {
        log.info("=== 检查序列化器配置 ===");
        
        log.info("myStringRedisTemplate配置:");
        log.info("  Key序列化器: {}", myStringRedisTemplate.getKeySerializer().getClass().getSimpleName());
        log.info("  Value序列化器: {}", myStringRedisTemplate.getValueSerializer().getClass().getSimpleName());
        
        log.info("redisTemplate配置:");
        log.info("  Key序列化器: {}", redisTemplate.getKeySerializer().getClass().getSimpleName());
        log.info("  Value序列化器: {}", redisTemplate.getValueSerializer().getClass().getSimpleName());
        
        log.info("stringRedisTemplate配置:");
        log.info("  Key序列化器: {}", stringRedisTemplate.getKeySerializer().getClass().getSimpleName());
        log.info("  Value序列化器: {}", stringRedisTemplate.getValueSerializer().getClass().getSimpleName());
    }

    @Test
    public void testBinaryDetection() {
        log.info("=== 测试二进制检测 ===");
        
        String testValue = "test_value_123";
        String testKey = "test:binary:";
        
        testBinaryDetectionForTemplate("myStringRedisTemplate", myStringRedisTemplate, testKey + "1", testValue);
        testBinaryDetectionForTemplate("redisTemplate", redisTemplate, testKey + "2", testValue);
        testBinaryDetectionForTemplate("stringRedisTemplate", stringRedisTemplate, testKey + "3", testValue);
    }

    private void testBinaryDetectionForTemplate(String templateName, RedisTemplate<String, String> template, String testKey, String testValue) {
        log.info("测试 {} 的二进制检测", templateName);
        
        try {
            template.opsForValue().set(testKey, testValue, 60);
            String retrievedValue = template.opsForValue().get(testKey);
            
            boolean isBinary = isBinaryString(retrievedValue);
            boolean isEqual = testValue.equals(retrievedValue);
            
            log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}, 是否二进制={}", 
                    templateName, testValue, retrievedValue, isEqual, isBinary);
            
            if (isBinary) {
                log.warn("  ⚠️ {}: 检测到二进制数据！", templateName);
                log.warn("    原始值字节: {}", Arrays.toString(testValue.getBytes(StandardCharsets.UTF_8)));
                log.warn("    读取值字节: {}", Arrays.toString(retrievedValue.getBytes(StandardCharsets.UTF_8)));
            }
            
            template.delete(testKey);
            
        } catch (Exception e) {
            log.error("  ❌ {}: 测试异常 - {}", templateName, e.getMessage());
        }
    }

    private boolean isBinaryString(String str) {
        if (str == null) return false;
        
        // 检查是否包含不可打印字符
        for (char c : str.toCharArray()) {
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        
        // 检查是否以JSON格式存储（可能被序列化为二进制）
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return true;
        }
        
        return false;
    }

    @Test
    public void testHashOperations() {
        log.info("=== 测试Hash操作 ===");
        
        String hashKey = "test:hash:user";
        String field1 = "name";
        String field2 = "age";
        String field3 = "email";
        
        String value1 = "张三";
        String value2 = "25";
        String value3 = "zhangsan@example.com";
        
        // 测试StringRedisTemplate的Hash操作
        log.info("--- 测试StringRedisTemplate Hash操作 ---");
        try {
            stringRedisTemplate.opsForHash().put(hashKey + ":string", field1, value1);
            stringRedisTemplate.opsForHash().put(hashKey + ":string", field2, value2);
            stringRedisTemplate.opsForHash().put(hashKey + ":string", field3, value3);
            
            String retrievedValue1 = (String) stringRedisTemplate.opsForHash().get(hashKey + ":string", field1);
            String retrievedValue2 = (String) stringRedisTemplate.opsForHash().get(hashKey + ":string", field2);
            String retrievedValue3 = (String) stringRedisTemplate.opsForHash().get(hashKey + ":string", field3);
            
            log.info("StringRedisTemplate Hash结果:");
            log.info("  field1: 原始值='{}', 读取值='{}', 是否相等={}", field1, value1, retrievedValue1, value1.equals(retrievedValue1));
            log.info("  field2: 原始值='{}', 读取值='{}', 是否相等={}", field2, value2, retrievedValue2, value2.equals(retrievedValue2));
            log.info("  field3: 原始值='{}', 读取值='{}', 是否相等={}", field3, value3, retrievedValue3, value3.equals(retrievedValue3));
            
            // 检查二进制问题
            if (!value1.equals(retrievedValue1)) {
                analyzeBinaryIssue(value1, retrievedValue1, "StringRedisTemplate Hash field1");
            }
            if (!value2.equals(retrievedValue2)) {
                analyzeBinaryIssue(value2, retrievedValue2, "StringRedisTemplate Hash field2");
            }
            if (!value3.equals(retrievedValue3)) {
                analyzeBinaryIssue(value3, retrievedValue3, "StringRedisTemplate Hash field3");
            }
            
            stringRedisTemplate.delete(hashKey + ":string");
            
        } catch (Exception e) {
            log.error("❌ StringRedisTemplate Hash操作异常: {}", e.getMessage(), e);
        }
        
        // 测试RedisTemplate的Hash操作
        log.info("--- 测试RedisTemplate Hash操作 ---");
        try {
            redisTemplate.opsForHash().put(hashKey + ":redis", field1, value1);
            redisTemplate.opsForHash().put(hashKey + ":redis", field2, value2);
            redisTemplate.opsForHash().put(hashKey + ":redis", field3, value3);
            
            String retrievedValue1 = (String) redisTemplate.opsForHash().get(hashKey + ":redis", field1);
            String retrievedValue2 = (String) redisTemplate.opsForHash().get(hashKey + ":redis", field2);
            String retrievedValue3 = (String) redisTemplate.opsForHash().get(hashKey + ":redis", field3);
            
            log.info("RedisTemplate Hash结果:");
            log.info("  field1: 原始值='{}', 读取值='{}', 是否相等={}", field1, value1, retrievedValue1, value1.equals(retrievedValue1));
            log.info("  field2: 原始值='{}', 读取值='{}', 是否相等={}", field2, value2, retrievedValue2, value2.equals(retrievedValue2));
            log.info("  field3: 原始值='{}', 读取值='{}', 是否相等={}", field3, value3, retrievedValue3, value3.equals(retrievedValue3));
            
            // 检查二进制问题
            if (!value1.equals(retrievedValue1)) {
                analyzeBinaryIssue(value1, retrievedValue1, "RedisTemplate Hash field1");
            }
            if (!value2.equals(retrievedValue2)) {
                analyzeBinaryIssue(value2, retrievedValue2, "RedisTemplate Hash field2");
            }
            if (!value3.equals(retrievedValue3)) {
                analyzeBinaryIssue(value3, retrievedValue3, "RedisTemplate Hash field3");
            }
            
            redisTemplate.delete(hashKey + ":redis");
            
        } catch (Exception e) {
            log.error("❌ RedisTemplate Hash操作异常: {}", e.getMessage(), e);
        }
        
        // 测试myStringRedisTemplate的Hash操作
        log.info("--- 测试myStringRedisTemplate Hash操作 ---");
        try {
            myStringRedisTemplate.opsForHash().put(hashKey + ":mystring", field1, value1);
            myStringRedisTemplate.opsForHash().put(hashKey + ":mystring", field2, value2);
            myStringRedisTemplate.opsForHash().put(hashKey + ":mystring", field3, value3);
            
            String retrievedValue1 = (String) myStringRedisTemplate.opsForHash().get(hashKey + ":mystring", field1);
            String retrievedValue2 = (String) myStringRedisTemplate.opsForHash().get(hashKey + ":mystring", field2);
            String retrievedValue3 = (String) myStringRedisTemplate.opsForHash().get(hashKey + ":mystring", field3);
            
            log.info("myStringRedisTemplate Hash结果:");
            log.info("  field1: 原始值='{}', 读取值='{}', 是否相等={}", field1, value1, retrievedValue1, value1.equals(retrievedValue1));
            log.info("  field2: 原始值='{}', 读取值='{}', 是否相等={}", field2, value2, retrievedValue2, value2.equals(retrievedValue2));
            log.info("  field3: 原始值='{}', 读取值='{}', 是否相等={}", field3, value3, retrievedValue3, value3.equals(retrievedValue3));
            
            // 检查二进制问题
            if (!value1.equals(retrievedValue1)) {
                analyzeBinaryIssue(value1, retrievedValue1, "myStringRedisTemplate Hash field1");
            }
            if (!value2.equals(retrievedValue2)) {
                analyzeBinaryIssue(value2, retrievedValue2, "myStringRedisTemplate Hash field2");
            }
            if (!value3.equals(retrievedValue3)) {
                analyzeBinaryIssue(value3, retrievedValue3, "myStringRedisTemplate Hash field3");
            }
            
            myStringRedisTemplate.delete(hashKey + ":mystring");
            
        } catch (Exception e) {
            log.error("❌ myStringRedisTemplate Hash操作异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testHashWithSpecialCharacters() {
        log.info("=== 测试Hash特殊字符 ===");
        
        String hashKey = "test:hash:special";
        String field1 = "name";
        String field2 = "description";
        String field3 = "data";
        
        String value1 = "测试用户@#$%";
        String value2 = "这是一个包含特殊字符的描述：!@#$%^&*()_+-=[]{}|;':\",./<>?";
        String value3 = "{\"key\":\"value\",\"number\":123,\"array\":[1,2,3]}";
        
        // 测试StringRedisTemplate
        testHashField("StringRedisTemplate", stringRedisTemplate, hashKey + ":string", field1, value1);
        testHashField("StringRedisTemplate", stringRedisTemplate, hashKey + ":string", field2, value2);
        testHashField("StringRedisTemplate", stringRedisTemplate, hashKey + ":string", field3, value3);
        
        // 测试RedisTemplate
        testHashField("RedisTemplate", redisTemplate, hashKey + ":redis", field1, value1);
        testHashField("RedisTemplate", redisTemplate, hashKey + ":redis", field2, value2);
        testHashField("RedisTemplate", redisTemplate, hashKey + ":redis", field3, value3);
        
        // 测试myStringRedisTemplate
        testHashField("myStringRedisTemplate", myStringRedisTemplate, hashKey + ":mystring", field1, value1);
        testHashField("myStringRedisTemplate", myStringRedisTemplate, hashKey + ":mystring", field2, value2);
        testHashField("myStringRedisTemplate", myStringRedisTemplate, hashKey + ":mystring", field3, value3);
    }

    @Test
    public void testHashBatchOperations() {
        log.info("=== 测试Hash批量操作 ===");
        
        String hashKey = "test:hash:batch";
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("field1", "value1");
        dataMap.put("field2", "value2");
        dataMap.put("field3", "value3");
        dataMap.put("field4", "测试中文");
        dataMap.put("field5", "special@#$%");
        
        // 测试StringRedisTemplate批量操作
        log.info("--- 测试StringRedisTemplate批量操作 ---");
        try {
            stringRedisTemplate.opsForHash().putAll(hashKey + ":string", dataMap);
            
            Map<Object, Object> retrievedMap = stringRedisTemplate.opsForHash().entries(hashKey + ":string");
            
            log.info("StringRedisTemplate批量操作结果:");
            for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                String originalValue = entry.getValue();
                String retrievedValue = (String) retrievedMap.get(entry.getKey());
                boolean isEqual = originalValue.equals(retrievedValue);
                log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", 
                        entry.getKey(), originalValue, retrievedValue, isEqual);
                
                if (!isEqual) {
                    analyzeBinaryIssue(originalValue, retrievedValue, "StringRedisTemplate Hash批量 " + entry.getKey());
                }
            }
            
            stringRedisTemplate.delete(hashKey + ":string");
            
        } catch (Exception e) {
            log.error("❌ StringRedisTemplate批量操作异常: {}", e.getMessage(), e);
        }
        
        // 测试RedisTemplate批量操作
        log.info("--- 测试RedisTemplate批量操作 ---");
        try {
            redisTemplate.opsForHash().putAll(hashKey + ":redis", dataMap);
            
            Map<Object, Object> retrievedMap = redisTemplate.opsForHash().entries(hashKey + ":redis");
            
            log.info("RedisTemplate批量操作结果:");
            for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                String originalValue = entry.getValue();
                String retrievedValue = (String) retrievedMap.get(entry.getKey());
                boolean isEqual = originalValue.equals(retrievedValue);
                log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", 
                        entry.getKey(), originalValue, retrievedValue, isEqual);
                
                if (!isEqual) {
                    analyzeBinaryIssue(originalValue, retrievedValue, "RedisTemplate Hash批量 " + entry.getKey());
                }
            }
            
            redisTemplate.delete(hashKey + ":redis");
            
        } catch (Exception e) {
            log.error("❌ RedisTemplate批量操作异常: {}", e.getMessage(), e);
        }
        
        // 测试myStringRedisTemplate批量操作
        log.info("--- 测试myStringRedisTemplate批量操作 ---");
        try {
            myStringRedisTemplate.opsForHash().putAll(hashKey + ":mystring", dataMap);
            
            Map<Object, Object> retrievedMap = myStringRedisTemplate.opsForHash().entries(hashKey + ":mystring");
            
            log.info("myStringRedisTemplate批量操作结果:");
            for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                String originalValue = entry.getValue();
                String retrievedValue = (String) retrievedMap.get(entry.getKey());
                boolean isEqual = originalValue.equals(retrievedValue);
                log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", 
                        entry.getKey(), originalValue, retrievedValue, isEqual);
                
                if (!isEqual) {
                    analyzeBinaryIssue(originalValue, retrievedValue, "myStringRedisTemplate Hash批量 " + entry.getKey());
                }
            }
            
            myStringRedisTemplate.delete(hashKey + ":mystring");
            
        } catch (Exception e) {
            log.error("❌ myStringRedisTemplate批量操作异常: {}", e.getMessage(), e);
        }
    }

    private void testHashField(String templateName, RedisTemplate<String, String> template, String hashKey, String field, String value) {
        try {
            template.opsForHash().put(hashKey, field, value);
            String retrievedValue = (String) template.opsForHash().get(hashKey, field);
            
            boolean isEqual = value.equals(retrievedValue);
            log.info("{} Hash {}: 原始值='{}', 读取值='{}', 是否相等={}", 
                    templateName, field, value, retrievedValue, isEqual);
            
            if (!isEqual) {
                analyzeBinaryIssue(value, retrievedValue, templateName + " Hash " + field);
            }
            
            template.delete(hashKey);
            
        } catch (Exception e) {
            log.error("❌ {} Hash操作异常 {}: {}", templateName, field, e.getMessage());
        }
    }

    private void analyzeBinaryIssue(String originalValue, String retrievedValue, String context) {
        log.error("❌ 二进制问题分析 - {}: 原始值='{}', 读取值='{}'", context, originalValue, retrievedValue);
        log.error("  原始值字节: {}", Arrays.toString(originalValue.getBytes(StandardCharsets.UTF_8)));
        log.error("  读取值字节: {}", Arrays.toString(retrievedValue.getBytes(StandardCharsets.UTF_8)));
    }
} 