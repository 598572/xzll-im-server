package com.xzll.auth.redis;

import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * RedissonUtils测试类
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
//@TestPropertySource(properties = {
//    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
//})
@Slf4j
public class RedissonUtilsTest {

    @Autowired
    private RedissonUtils redissonUtils;

    @Test
    public void testStringOperations() {
        log.info("=== 测试String操作 ===");
        
        String key = "test:string:basic";
        String value = "hello world";
        
        // 设置值
        redissonUtils.setString(key, value);
        log.info("✅ 设置String: key={}, value={}", key, value);
        
        // 获取值
        String retrievedValue = redissonUtils.getString(key);
        log.info("📖 获取String: key={}, value={}", key, retrievedValue);
        
        // 检查是否相等
        boolean isEqual = value.equals(retrievedValue);
        log.info("🔍 String值是否相等: {}", isEqual);
        
        // 设置过期时间
        redissonUtils.setString(key + ":expire", value, 60, TimeUnit.SECONDS);
        log.info("⏰ 设置String过期时间: key={}", key + ":expire");
        
        // 检查存在性
        boolean exists = redissonUtils.existsString(key);
        log.info("🔍 String是否存在: {}", exists);
        
        // 清理
        redissonUtils.deleteString(key);
        redissonUtils.deleteString(key + ":expire");
        log.info("🧹 清理String测试数据");
    }

    @Test
    public void testHashOperations() {
        log.info("=== 测试Hash操作 ===");
        
        String key = "test:hash:user";
        String field1 = "name";
        String field2 = "age";
        String field3 = "email";
        
        String value1 = "张三";
        String value2 = "25";
        String value3 = "zhangsan@example.com";
        
        // 设置单个字段
        redissonUtils.setHash(key, field1, value1);
        redissonUtils.setHash(key, field2, value2);
        redissonUtils.setHash(key, field3, value3);
        log.info("✅ 设置Hash字段: key={}", key);
        
        // 获取单个字段
        String retrievedValue1 = redissonUtils.getHash(key, field1);
        String retrievedValue2 = redissonUtils.getHash(key, field2);
        String retrievedValue3 = redissonUtils.getHash(key, field3);
        
        log.info("📖 Hash字段值:");
        log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", field1, value1, retrievedValue1, value1.equals(retrievedValue1));
        log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", field2, value2, retrievedValue2, value2.equals(retrievedValue2));
        log.info("  {}: 原始值='{}', 读取值='{}', 是否相等={}", field3, value3, retrievedValue3, value3.equals(retrievedValue3));
        
        // 批量设置
        Map<String, String> batchMap = new HashMap<>();
        batchMap.put("city", "北京");
        batchMap.put("job", "程序员");
        redissonUtils.setHash(key + ":batch", batchMap);
        log.info("✅ 批量设置Hash: key={}", key + ":batch");
        
        // 获取所有字段
        Map<String, String> allFields = redissonUtils.getAllHash(key + ":batch");
        log.info("📖 所有Hash字段: {}", allFields);
        
        // 检查字段存在性
        boolean exists = redissonUtils.existsHash(key, field1);
        log.info("🔍 Hash字段是否存在: {}", exists);
        
        // 获取字段数量
        long size = redissonUtils.sizeHash(key);
        log.info("📊 Hash字段数量: {}", size);
        
        // 清理
        redissonUtils.delete(key);
        redissonUtils.delete(key + ":batch");
        log.info("🧹 清理Hash测试数据");
    }

    @Test
    public void testListOperations() {
        log.info("=== 测试List操作 ===");
        
        String key = "test:list:queue";
        String[] values = {"item1", "item2", "item3", "item4", "item5"};
        
        // 右侧推入
        redissonUtils.pushRight(key, values);
        log.info("✅ 右侧推入List: key={}, values={}", key, Arrays.toString(values));
        
        // 获取长度
        long size = redissonUtils.sizeList(key);
        log.info("📊 List长度: {}", size);
        
        // 获取范围
        List<String> range = redissonUtils.getListRange(key, 0, 2);
        log.info("📖 List范围[0-2]: {}", range);
        
        // 左侧弹出
        String leftPop = redissonUtils.popLeft(key);
        log.info("⬅️ 左侧弹出: {}", leftPop);
        
        // 右侧弹出
        String rightPop = redissonUtils.popRight(key);
        log.info("➡️ 右侧弹出: {}", rightPop);
        
        // 左侧推入
        redissonUtils.pushLeft(key, "newItem");
        log.info("⬅️ 左侧推入: newItem");
        
        // 清理
        redissonUtils.delete(key);
        log.info("🧹 清理List测试数据");
    }

    @Test
    public void testSetOperations() {
        log.info("=== 测试Set操作 ===");
        
        String key = "test:set:tags";
        String[] values = {"java", "spring", "redis", "redisson"};
        
        // 添加元素
        redissonUtils.addSet(key, values);
        log.info("✅ 添加Set元素: key={}, values={}", key, Arrays.toString(values));
        
        // 获取所有元素
        Set<String> allElements = redissonUtils.getAllSet(key);
        log.info("📖 所有Set元素: {}", allElements);
        
        // 检查元素存在性
        boolean exists = redissonUtils.existsSet(key, "java");
        log.info("🔍 Set元素是否存在: {}", exists);
        
        // 获取大小
        long size = redissonUtils.sizeSet(key);
        log.info("📊 Set大小: {}", size);
        
        // 删除元素
        redissonUtils.removeSet(key, "redis");
        log.info("🗑️ 删除Set元素: redis");
        
        // 测试交集和并集
        String key1 = "test:set:set1";
        String key2 = "test:set:set2";
        
        redissonUtils.addSet(key1, "a", "b", "c");
        redissonUtils.addSet(key2, "b", "c", "d");
        
        // 注意：RedissonUtils中移除了intersectSet和unionSet方法，这里跳过测试
        log.info("🔗 Set交集和并集测试已跳过（方法已移除）");
        
        // 清理
        redissonUtils.deleteKeys(key, key1, key2);
        log.info("🧹 清理Set测试数据");
    }

    @Test
    public void testZSetOperations() {
        log.info("=== 测试ZSet操作 ===");
        
        String key = "test:zset:ranking";
        
        // 添加元素
        redissonUtils.addZSet(key, "player1", 100.0);
        redissonUtils.addZSet(key, "player2", 200.0);
        redissonUtils.addZSet(key, "player3", 150.0);
        redissonUtils.addZSet(key, "player4", 300.0);
        log.info("✅ 添加ZSet元素: key={}", key);
        
        // 批量添加
        Map<String, Double> scoreMembers = new HashMap<>();
        scoreMembers.put("player5", 250.0);
        scoreMembers.put("player6", 180.0);
        redissonUtils.addZSet(key, scoreMembers);
        log.info("✅ 批量添加ZSet元素");
        
        // 获取分数
        Double score = redissonUtils.getZSetScore(key, "player1");
        log.info("📊 ZSet元素分数: player1={}", score);
        
        // 获取范围（升序）
        Collection<String> range = redissonUtils.getZSetRange(key, 0, 2);
        log.info("📖 ZSet升序范围[0-2]: {}", range);
        
        // 获取范围（降序）
        Collection<String> revRange = redissonUtils.getZSetRevRange(key, 0, 2);
        log.info("📖 ZSet降序范围[0-2]: {}", revRange);
        
        // 获取大小
        long size = redissonUtils.sizeZSet(key);
        log.info("📊 ZSet大小: {}", size);
        
        // 清理
        redissonUtils.delete(key);
        log.info("🧹 清理ZSet测试数据");
    }

    @Test
    public void testAdvancedFeatures() {
        log.info("=== 测试高级功能 ===");
        
        // 测试分布式锁
        String lockKey = "test:lock:distributed";
        RLock lock = redissonUtils.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (locked) {
                log.info("🔒 获取分布式锁成功: {}", lockKey);
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            log.error("分布式锁测试异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("🔓 释放分布式锁: {}", lockKey);
            }
        }

        // 测试限流器
        String rateLimiterKey = "test:rate:limiter";
        RRateLimiter rateLimiter = redissonUtils.getRateLimiter(rateLimiterKey);
        // 初始化限流器：每秒10个请求
        rateLimiter.trySetRate(org.redisson.api.RateType.OVERALL, 10, 1, org.redisson.api.RateIntervalUnit.SECONDS);
        
        for (int i = 0; i < 5; i++) {
            boolean acquired = rateLimiter.tryAcquire();
            log.info("🚦 限流器测试 {}: {}", i + 1, acquired ? "通过" : "被限流");
        }
        
        // 测试计数器
        String counterKey = "test:counter:atomic";
        redissonUtils.getAtomicLong(counterKey).set(0);
        
        for (int i = 0; i < 5; i++) {
            long value = redissonUtils.getAtomicLong(counterKey).incrementAndGet();
            log.info("🔢 计数器测试 {}: {}", i + 1, value);
        }
        
        // 测试布隆过滤器
        String bloomFilterKey = "test:bloom:filter";
        redissonUtils.initBloomFilter(bloomFilterKey, 10000, 0.01);
        
        String[] testValues = {"user1", "user2", "user3", "user4", "user5"};
        for (String value : testValues) {
            redissonUtils.addToBloomFilter(bloomFilterKey, value);
            boolean contains = redissonUtils.containsInBloomFilter(bloomFilterKey, value);
            log.info("🌸 布隆过滤器测试: value={}, contains={}", value, contains);
        }
        
        // 测试BitMap
        String bitSetKey = "test:bitset:online";
        for (int i = 0; i < 10; i++) {
            redissonUtils.setBit(bitSetKey, i, i % 2 == 0);
            boolean bit = redissonUtils.getBit(bitSetKey, i);
            log.info("🔢 BitMap测试: index={}, value={}", i, bit);
        }
        
        long bitCount = redissonUtils.getBitCount(bitSetKey);
        log.info("📊 BitMap中1的个数: {}", bitCount);
        
        // 测试HyperLogLog
        String hyperLogLogKey = "test:hll:uv";
        String[] uvValues = {"user1", "user2", "user3", "user1", "user4", "user2"};
        redissonUtils.addToHyperLogLog(hyperLogLogKey, uvValues);
        
        long uvCount = redissonUtils.getHyperLogLogCount(hyperLogLogKey);
        log.info("📊 HyperLogLog基数: {}", uvCount);
        
        // 清理
        redissonUtils.deleteKeys(lockKey, rateLimiterKey, counterKey, bloomFilterKey, bitSetKey, hyperLogLogKey);
        log.info("🧹 清理高级功能测试数据");
    }

    @Test
    public void testCommonOperations() {
        log.info("=== 测试通用操作 ===");
        
        String key1 = "test:common:key1";
        String key2 = "test:common:key2";
        String key3 = "test:common:key3";
        
        // 设置值
        redissonUtils.setString(key1, "value1");
        redissonUtils.setString(key2, "value2");
        redissonUtils.setString(key3, "value3");
        
        // 检查存在性
        boolean exists1 = redissonUtils.exists(key1);
        boolean exists2 = redissonUtils.exists(key2);
        log.info("🔍 键存在性检查: key1={}, key2={}", exists1, exists2);
        
        // 获取键类型
        String type1 = redissonUtils.getType(key1);
        log.info("📋 键类型: key1={}", type1);
        
        // 批量删除
        long deletedCount = redissonUtils.deleteKeys(key1, key2, key3);
        log.info("🗑️ 批量删除键数量: {}", deletedCount);
        
        // 获取键数量
        long totalKeys = redissonUtils.getKeysCount();
        log.info("📊 总键数量: {}", totalKeys);
        
        log.info("✅ 通用操作测试完成");
    }

    @Test
    public void testBinaryIssueVerification() {
        log.info("=== 测试二进制问题验证 ===");
        
        String testKey = "test:binary:verification";
        String originalValue = "测试中文内容@#$%^&*()_+-=[]{}|;':\",./<>?";
        
        // 存储
        redissonUtils.setString(testKey, originalValue);
        log.info("✅ 存储原始值: '{}'", originalValue);
        
        // 读取
        String retrievedValue = redissonUtils.getString(testKey);
        log.info("📖 读取值: '{}'", retrievedValue);
        
        // 检查是否相等
        boolean isEqual = originalValue.equals(retrievedValue);
        log.info("🔍 值是否相等: {}", isEqual);
        
        if (!isEqual) {
            log.error("❌ 发现二进制问题！");
            log.error("原始值长度: {}", originalValue.length());
            log.error("读取值长度: {}", retrievedValue.length());
            log.error("原始值字节: {}", Arrays.toString(originalValue.getBytes()));
            log.error("读取值字节: {}", Arrays.toString(retrievedValue.getBytes()));
        } else {
            log.info("✅ 没有发现二进制问题，Redisson工作正常！");
        }
        
        // 清理
        redissonUtils.delete(testKey);
        log.info("🧹 清理二进制验证测试数据");
    }
} 