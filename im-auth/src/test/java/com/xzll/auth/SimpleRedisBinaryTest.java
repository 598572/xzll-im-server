package com.xzll.auth;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@Slf4j
public class SimpleRedisBinaryTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void testStringRedisTemplateBinaryIssue() {
        log.info("=== 测试 StringRedisTemplate 二进制问题 ===");
        
        String testValue = "test_value_123";
        String testKey = "test:binary:stringRedisTemplate";
        
        try {
            stringRedisTemplate.opsForValue().set(testKey, testValue, 60);
            log.info("✅ 存储成功: key={}, value={}", testKey, testValue);
            
            String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
            log.info("📖 读取结果: key={}, retrievedValue={}", testKey, retrievedValue);
            
            boolean isEqual = testValue.equals(retrievedValue);
            log.info("🔍 值是否相等: {}", isEqual);
            
            analyzeBinaryIssue(testValue, retrievedValue, "StringRedisTemplate");
            
            stringRedisTemplate.delete(testKey);
            log.info("🧹 清理完成: {}", testKey);
            
        } catch (Exception e) {
            log.error("❌ 测试异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testRedisTemplateBinaryIssue() {
        log.info("=== 测试 RedisTemplate 二进制问题 ===");
        
        String testValue = "test_value_456";
        String testKey = "test:binary:redisTemplate";
        
        try {
            redisTemplate.opsForValue().set(testKey, testValue, 60);
            log.info("✅ 存储成功: key={}, value={}", testKey, testValue);
            
            String retrievedValue = redisTemplate.opsForValue().get(testKey);
            log.info("📖 读取结果: key={}, retrievedValue={}", testKey, retrievedValue);
            
            boolean isEqual = testValue.equals(retrievedValue);
            log.info("🔍 值是否相等: {}", isEqual);
            
            analyzeBinaryIssue(testValue, retrievedValue, "RedisTemplate");
            
            redisTemplate.delete(testKey);
            log.info("🧹 清理完成: {}", testKey);
            
        } catch (Exception e) {
            log.error("❌ 测试异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testSerializerConfiguration() {
        log.info("=== 检查序列化器配置 ===");
        
        log.info("StringRedisTemplate序列化器:");
        log.info("  Key序列化器: {}", stringRedisTemplate.getKeySerializer().getClass().getSimpleName());
        log.info("  Value序列化器: {}", stringRedisTemplate.getValueSerializer().getClass().getSimpleName());
        
        log.info("RedisTemplate序列化器:");
        log.info("  Key序列化器: {}", redisTemplate.getKeySerializer().getClass().getSimpleName());
        log.info("  Value序列化器: {}", redisTemplate.getValueSerializer().getClass().getSimpleName());
    }

    @Test
    public void testByteAnalysis() {
        log.info("=== 字节级别分析 ===");
        
        String testValue = "test_value_789";
        String testKey = "test:byte:analysis";
        
        try {
            stringRedisTemplate.opsForValue().set(testKey, testValue, 60);
            String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
            
            byte[] originalBytes = testValue.getBytes(StandardCharsets.UTF_8);
            byte[] retrievedBytes = retrievedValue.getBytes(StandardCharsets.UTF_8);
            
            log.info("原始值字节: {}", Arrays.toString(originalBytes));
            log.info("读取值字节: {}", Arrays.toString(retrievedBytes));
            log.info("原始值字节长度: {}", originalBytes.length);
            log.info("读取值字节长度: {}", retrievedBytes.length);
            
            int nullCount = 0;
            for (byte b : retrievedBytes) {
                if (b == 0) {
                    nullCount++;
                }
            }
            log.info("读取值中null字符数量: {}", nullCount);
            
            stringRedisTemplate.delete(testKey);
            
        } catch (Exception e) {
            log.error("❌ 字节分析异常: {}", e.getMessage(), e);
        }
    }

    private void analyzeBinaryIssue(String originalValue, String retrievedValue, String context) {
        log.info("🔍 二进制问题分析 - {}", context);
        
        if (originalValue == null || retrievedValue == null) {
            log.warn("⚠️ 值为null，跳过分析");
            return;
        }
        
        log.info("  原始值长度: {}", originalValue.length());
        log.info("  读取值长度: {}", retrievedValue.length());
        
        boolean hasNullChars = retrievedValue.contains("\u0000");
        log.info("  是否包含null字符: {}", hasNullChars);
        
        if (hasNullChars) {
            int nullCount = 0;
            for (char c : retrievedValue.toCharArray()) {
                if (c == '\u0000') {
                    nullCount++;
                }
            }
            log.info("  null字符数量: {}", nullCount);
            
            int firstNonNullIndex = -1;
            for (int i = 0; i < retrievedValue.length(); i++) {
                if (retrievedValue.charAt(i) != '\u0000') {
                    firstNonNullIndex = i;
                    break;
                }
            }
            log.info("  第一个非null字符位置: {}", firstNonNullIndex);
            
            if (firstNonNullIndex >= 0) {
                String validContent = retrievedValue.substring(firstNonNullIndex);
                log.info("  有效内容: '{}'", validContent);
                log.info("  有效内容是否与原始值相等: {}", originalValue.equals(validContent));
            }
        }
        
        byte[] originalBytes = originalValue.getBytes(StandardCharsets.UTF_8);
        byte[] retrievedBytes = retrievedValue.getBytes(StandardCharsets.UTF_8);
        
        log.info("  原始值字节长度: {}", originalBytes.length);
        log.info("  读取值字节长度: {}", retrievedBytes.length);
        
        int checkLength = Math.min(10, retrievedBytes.length);
        log.info("  读取值前{}个字节: {}", checkLength, 
                Arrays.toString(Arrays.copyOf(retrievedBytes, checkLength)));
    }
} 