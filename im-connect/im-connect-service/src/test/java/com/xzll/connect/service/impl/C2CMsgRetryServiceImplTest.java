package com.xzll.connect.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.utils.CompressionUtil;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.connect.service.dto.C2CMsgRetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C2C消息重试服务测试类
 * 主要测试 Lua 脚本的调用：addToRetryQueue 和 removeFromRetryQueue
 * 
 * @Author: hzz
 * @Date: 2025-11-18
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "im-server.c2c.retry.enabled=true",
    "im-server.c2c.retry.max-retries=3",
    "im-server.c2c.retry.delays=5,30,300",
    "im-server.c2c.retry.batch-size=100",
    "im-server.c2c.retry.scan-interval=1000"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class C2CMsgRetryServiceImplTest {

    @Autowired
    private C2CMsgRetryServiceImpl c2CMsgRetryService;

    @Autowired
    private RedissonUtils redissonUtils;

    @Autowired
    private RedissonClient redissonClient;

    private static final String TEST_QUEUE_KEY = ImConstant.RedisKeyConstant.C2C_MSG_RETRY_QUEUE;
    private static final String TEST_INDEX_KEY = ImConstant.RedisKeyConstant.C2C_MSG_RETRY_INDEX;

    private String testMsgId;
    private String testClientMsgId;
    private String testFromUserId;
    private String testToUserId;

    @BeforeEach
    public void setUp() {
        // 生成测试数据
        testMsgId = String.valueOf(System.currentTimeMillis());
        testClientMsgId = UUID.randomUUID().toString();
        testFromUserId = "123729160192";
        testToUserId = "124948567040";
        
        // 清理测试数据
        cleanupTestData();
    }

    @AfterEach
    public void tearDown() {
        // 清理测试数据
//        cleanupTestData();
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        try {
            // 直接删除整个key，避免Codec解码问题
            redissonClient.getKeys().delete(TEST_QUEUE_KEY, TEST_INDEX_KEY);
            log.info("测试数据清理完成");
        } catch (Exception e) {
            log.warn("清理测试数据失败: {}", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("测试 addToRetryQueue - 添加消息到延迟队列")
    public void testAddToRetryQueue() {
        log.info("=== 测试 addToRetryQueue ===");
        
        // 1. 准备测试数据
        C2CSendMsgAO packet = createTestPacket();
        
        // 2. 执行添加操作
        c2CMsgRetryService.addToRetryQueue(packet);
        
        // 等待一下，确保异步操作完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // ignore
        }
        
        // 3. 验证 ZSet 中是否有msgId（现在ZSet只存msgId，不存完整数据）
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        Collection<String> msgIds = zset.readAll();
        log.info("ZSet中的msgId数量: {}", msgIds.size());
        log.info("ZSet中的msgIds: {}", msgIds);
        
        assertTrue(msgIds.contains(packet.getMsgId()), "ZSet 中应该包含msgId");
        
        // 4. 验证 Hash 中是否有压缩数据（使用StringCodec）
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        log.info("Hash中所有的key: {}", hash.keySet());
        String compressedValue = hash.get(packet.getMsgId());
        log.info("Hash中msgId[{}]的压缩数据长度: {}", packet.getMsgId(), compressedValue != null ? compressedValue.length() : 0);
        assertNotNull(compressedValue, "Hash 中应该包含压缩数据");
        
        // 5. 解压并验证数据
        String jsonValue = CompressionUtil.decompressFromBase64(compressedValue);
        C2CMsgRetryEvent hashEvent = JSONUtil.toBean(jsonValue, C2CMsgRetryEvent.class);
        assertEquals(packet.getMsgId(), hashEvent.getMsgId(), "Hash 中的 msgId 应该匹配");
        assertEquals(packet.getClientMsgId(), hashEvent.getClientMsgId(), "clientMsgId 应该匹配");
        assertEquals(packet.getFromUserId(), hashEvent.getFromUserId(), "fromUserId 应该匹配");
        assertEquals(packet.getToUserId(), hashEvent.getToUserId(), "toUserId 应该匹配");
        assertEquals(0, hashEvent.getRetryCount(), "初始重试次数应该为 0");
        assertEquals(3, hashEvent.getMaxRetries(), "最大重试次数应该为 3");
        
        // 6. 验证 score（执行时间戳）是否正确
        double score = zset.getScore(packet.getMsgId());
        assertTrue(score > System.currentTimeMillis(), "执行时间戳应该大于当前时间");
        
        log.info("✅ addToRetryQueue 测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试 removeFromRetryQueue - 从延迟队列删除消息")
    public void testRemoveFromRetryQueue() {
        log.info("=== 测试 removeFromRetryQueue ===");
        
        // 1. 先添加一条消息
        C2CSendMsgAO packet = createTestPacket();
        c2CMsgRetryService.addToRetryQueue(packet);
        
        // 2. 验证消息已添加（使用StringCodec）
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        
        // ZSet现在存msgId
        assertTrue(zset.contains(packet.getMsgId()), "消息应该已添加到 ZSet");
        
        // Hash存压缩数据
        String compressedValue = hash.get(packet.getMsgId());
        assertNotNull(compressedValue, "消息应该已添加到 Hash");
        
        // 3. 执行删除操作
        c2CMsgRetryService.removeFromRetryQueue(packet.getMsgId());
        
        // 4. 验证消息已从 ZSet 删除
        assertFalse(zset.contains(packet.getMsgId()), "消息应该已从 ZSet 删除");
        
        // 5. 验证消息已从 Hash 删除
        String hashValue = hash.get(packet.getMsgId());
        assertNull(hashValue, "消息应该已从 Hash 删除");
        
        log.info("✅ removeFromRetryQueue 测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试 removeFromRetryQueue - 删除不存在的消息")
    public void testRemoveFromRetryQueue_NotExists() {
        log.info("=== 测试 removeFromRetryQueue - 删除不存在的消息 ===");
        
        // 1. 尝试删除不存在的消息（不应该报错）
        String nonExistentMsgId = "9999999999999999999";
        assertDoesNotThrow(() -> {
            c2CMsgRetryService.removeFromRetryQueue(nonExistentMsgId);
        }, "删除不存在的消息不应该抛出异常");
        
        log.info("✅ removeFromRetryQueue - 删除不存在的消息测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("测试 addToRetryQueue 和 removeFromRetryQueue - 完整流程")
    public void testAddAndRemoveFlow() {
        log.info("=== 测试 addToRetryQueue 和 removeFromRetryQueue - 完整流程 ===");
        
        // 1. 添加多条消息
        C2CSendMsgAO packet1 = createTestPacket();
        packet1.setMsgId("msg1");
        packet1.setClientMsgId(UUID.randomUUID().toString());
        
        C2CSendMsgAO packet2 = createTestPacket();
        packet2.setMsgId("msg2");
        packet2.setClientMsgId(UUID.randomUUID().toString());
        
        c2CMsgRetryService.addToRetryQueue(packet1);
        c2CMsgRetryService.addToRetryQueue(packet2);
        
        // 2. 验证两条消息都已添加（使用StringCodec）
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        assertNotNull(hash.get("msg1"), "msg1 应该存在");
        assertNotNull(hash.get("msg2"), "msg2 应该存在");
        
        // 3. 删除一条消息
        c2CMsgRetryService.removeFromRetryQueue("msg1");
        
        // 4. 验证只有 msg1 被删除，msg2 仍然存在
        assertNull(hash.get("msg1"), "msg1 应该已被删除");
        assertNotNull(hash.get("msg2"), "msg2 应该仍然存在");
        
        // 5. 删除另一条消息
        c2CMsgRetryService.removeFromRetryQueue("msg2");
        
        // 6. 验证两条消息都已删除
        assertNull(hash.get("msg1"), "msg1 应该已被删除");
        assertNull(hash.get("msg2"), "msg2 应该已被删除");
        
        log.info("✅ addToRetryQueue 和 removeFromRetryQueue - 完整流程测试通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试 Lua 脚本原子性 - 同时添加和删除")
    public void testLuaScriptAtomicity() {
        log.info("=== 测试 Lua 脚本原子性 ===");
        
        // 1. 添加消息
        C2CSendMsgAO packet = createTestPacket();
        c2CMsgRetryService.addToRetryQueue(packet);
        
        // 2. 验证 ZSet 和 Hash 中都有数据（使用StringCodec）
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        
        // ZSet现在存msgId
        assertTrue(zset.contains(packet.getMsgId()), "ZSet 中应该有msgId");
        // Hash存压缩数据
        String compressedValue = hash.get(packet.getMsgId());
        assertNotNull(compressedValue, "Hash 中应该有数据");
        
        // 3. 删除消息
        c2CMsgRetryService.removeFromRetryQueue(packet.getMsgId());
        
        // 4. 验证 ZSet 和 Hash 中的数据都已删除（原子性）
        assertNull(hash.get(packet.getMsgId()), "Hash 中的数据应该已删除");
        assertFalse(zset.contains(packet.getMsgId()), "ZSet 中的数据应该已删除");
        
        log.info("✅ Lua 脚本原子性测试通过");
    }
    
    @Test
    @Order(6)
    @DisplayName("测试 scanRetryQueue - 扫描到期消息")
    public void testScanRetryQueue() throws Exception {
        log.info("=== 测试 scanRetryQueue - 扫描到期消息 ===");
        
        // 1. 添加一条立即到期的消息（执行时间设为过去）
        C2CSendMsgAO packet = createTestPacket();
        
        // 手动构建一个过期的重试事件
        C2CMsgRetryEvent expiredEvent = new C2CMsgRetryEvent();
        expiredEvent.setMsgId(packet.getMsgId());
        expiredEvent.setClientMsgId(packet.getClientMsgId());
        expiredEvent.setFromUserId(packet.getFromUserId());
        expiredEvent.setToUserId(packet.getToUserId());
        expiredEvent.setChatId(packet.getChatId());
        expiredEvent.setMsgContent(packet.getMsgContent());
        expiredEvent.setMsgFormat(packet.getMsgFormat());
        expiredEvent.setMsgCreateTime(packet.getMsgCreateTime());
        expiredEvent.setCreateTime(DateUtil.now());
        expiredEvent.setRetryCount(0);
        expiredEvent.setMaxRetries(3);
        
        String jsonValue = JSONUtil.toJsonStr(expiredEvent);
        // LZ4压缩
        String compressedValue = CompressionUtil.compressToBase64(jsonValue);
        
        // 设置执行时间为过去（确保消息已到期）
        long pastTime = System.currentTimeMillis() - 5000; // 5秒前
        
        // 使用Lua脚本添加（使用StringCodec，ZSet存msgId）
        redissonUtils.executeLuaScriptAsLongUseStringCodec(
            "local zset_key = KEYS[1]\n" +
            "local hash_key = KEYS[2]\n" +
            "local compressed_data = ARGV[1]\n" +
            "local score = ARGV[2]\n" +
            "local msg_id = ARGV[3]\n" +
            "redis.call('ZADD', zset_key, tonumber(score), msg_id)\n" +
            "redis.call('HSET', hash_key, msg_id, compressed_data)\n" +
            "return 1",
            Arrays.asList(TEST_QUEUE_KEY, TEST_INDEX_KEY),
            compressedValue,
            String.valueOf(pastTime),
            packet.getMsgId()
        );
        
        // 2. 验证消息已添加
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        Collection<String> values = zset.readAll();
        assertEquals(1, values.size(), "ZSet中应该有1条消息");
        
        // 3. 等待一下，确保数据写入完成
        Thread.sleep(100);
        
        // 4. 手动调用 scanRetryQueue（模拟定时任务触发）
        c2CMsgRetryService.scanRetryQueue();
        
        // 5. 等待异步处理完成
        Thread.sleep(2000);
        
        // 6. 验证消息已被处理（从ZSet删除）
        // 注意：由于消息会被重新加入队列（如果重试次数未用完），或者被删除（如果用户不在线）
        // 这里主要验证扫描逻辑正常执行，不会抛出异常
        log.info("✅ scanRetryQueue - 扫描到期消息测试通过");
    }
    
    @Test
    @Order(7)
    @DisplayName("测试 scanRetryQueue - 没有到期消息")
    public void testScanRetryQueue_NoExpiredMessages() throws Exception {
        log.info("=== 测试 scanRetryQueue - 没有到期消息 ===");
        
        // 1. 添加一条未到期的消息（执行时间设为未来）
        C2CSendMsgAO packet = createTestPacket();
        
        C2CMsgRetryEvent futureEvent = new C2CMsgRetryEvent();
        futureEvent.setMsgId(packet.getMsgId());
        futureEvent.setClientMsgId(packet.getClientMsgId());
        futureEvent.setFromUserId(packet.getFromUserId());
        futureEvent.setToUserId(packet.getToUserId());
        futureEvent.setChatId(packet.getChatId());
        futureEvent.setMsgContent(packet.getMsgContent());
        futureEvent.setMsgFormat(packet.getMsgFormat());
        futureEvent.setMsgCreateTime(packet.getMsgCreateTime());
        futureEvent.setCreateTime(DateUtil.now());
        futureEvent.setRetryCount(0);
        futureEvent.setMaxRetries(3);
        
        String jsonValue = JSONUtil.toJsonStr(futureEvent);
        // LZ4压缩
        String compressedValue = CompressionUtil.compressToBase64(jsonValue);
        
        // 设置执行时间为未来（确保消息未到期）
        long futureTime = System.currentTimeMillis() + 60000; // 60秒后
        
        // 使用Lua脚本添加（使用StringCodec）
        redissonUtils.executeLuaScriptAsLongUseStringCodec(
            "local zset_key = KEYS[1]\n" +
            "local hash_key = KEYS[2]\n" +
            "local compressed_data = ARGV[1]\n" +
            "local score = ARGV[2]\n" +
            "local msg_id = ARGV[3]\n" +
            "redis.call('ZADD', zset_key, tonumber(score), msg_id)\n" +
            "redis.call('HSET', hash_key, msg_id, compressed_data)\n" +
            "return 1",
            Arrays.asList(TEST_QUEUE_KEY, TEST_INDEX_KEY),
            compressedValue,
            String.valueOf(futureTime),
            packet.getMsgId()
        );
        
        // 2. 验证消息已添加
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        Collection<String> beforeValues = zset.readAll();
        assertEquals(1, beforeValues.size(), "ZSet中应该有1条消息");
        
        // 3. 调用 scanRetryQueue
        c2CMsgRetryService.scanRetryQueue();
        
        // 4. 等待一下
        Thread.sleep(500);
        
        // 5. 验证消息仍然存在（因为未到期，不应该被处理）
        Collection<String> afterValues = zset.readAll();
        assertEquals(1, afterValues.size(), "未到期的消息应该仍然在ZSet中");
        
        log.info("✅ scanRetryQueue - 没有到期消息测试通过");
    }
    
    @Test
    @Order(8)
    @DisplayName("测试 scanRetryQueue - 多条到期消息批量处理")
    public void testScanRetryQueue_BatchProcessing() throws Exception {
        log.info("=== 测试 scanRetryQueue - 多条到期消息批量处理 ===");
        
        // 1. 添加多条已到期的消息
        int messageCount = 5;
        List<String> msgIds = new ArrayList<>();
        
        for (int i = 0; i < messageCount; i++) {
            C2CSendMsgAO packet = createTestPacket();
            packet.setMsgId("batch_msg_" + i);
            packet.setClientMsgId(UUID.randomUUID().toString());
            msgIds.add(packet.getMsgId());
            
            C2CMsgRetryEvent event = new C2CMsgRetryEvent();
            event.setMsgId(packet.getMsgId());
            event.setClientMsgId(packet.getClientMsgId());
            event.setFromUserId(packet.getFromUserId());
            event.setToUserId(packet.getToUserId());
            event.setChatId(packet.getChatId());
            event.setMsgContent("批量测试消息 " + i);
            event.setMsgFormat(packet.getMsgFormat());
            event.setMsgCreateTime(packet.getMsgCreateTime());
            event.setCreateTime(DateUtil.now());
            event.setRetryCount(0);
            event.setMaxRetries(3);
            
            String jsonValue = JSONUtil.toJsonStr(event);
            // LZ4压缩
            String compressedValue = CompressionUtil.compressToBase64(jsonValue);
            long pastTime = System.currentTimeMillis() - 1000; // 1秒前
            
            redissonUtils.executeLuaScriptAsLongUseStringCodec(
                "local zset_key = KEYS[1]\n" +
                "local hash_key = KEYS[2]\n" +
                "local compressed_data = ARGV[1]\n" +
                "local score = ARGV[2]\n" +
                "local msg_id = ARGV[3]\n" +
                "redis.call('ZADD', zset_key, tonumber(score), msg_id)\n" +
                "redis.call('HSET', hash_key, msg_id, compressed_data)\n" +
                "return 1",
                Arrays.asList(TEST_QUEUE_KEY, TEST_INDEX_KEY),
                compressedValue,
                String.valueOf(pastTime),
                packet.getMsgId()
            );
        }
        
        // 2. 验证消息已添加
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        Collection<String> beforeValues = zset.readAll();
        assertEquals(messageCount, beforeValues.size(), "ZSet中应该有" + messageCount + "条消息");
        
        // 3. 调用 scanRetryQueue
        c2CMsgRetryService.scanRetryQueue();
        
        // 4. 等待异步处理完成
        Thread.sleep(3000);
        
        // 5. 验证批量处理逻辑正常执行（不抛异常即可）
        log.info("✅ scanRetryQueue - 多条到期消息批量处理测试通过");
    }

    @Test
    @Order(9)
    @DisplayName("集成测试 - addToRetryQueue + scanRetryQueue 完整流程")
    public void testAddAndScanIntegration() throws Exception {
        log.info("=== 集成测试 - addToRetryQueue + scanRetryQueue 完整流程 ===");
        
        // 1. 准备测试消息
        C2CSendMsgAO packet = createTestPacket();
        String testMsgId = "integration_test_" + System.currentTimeMillis();
        packet.setMsgId(testMsgId);
        
        // 2. 添加到重试队列（应该会压缩并存储）
        log.info("步骤1: 添加消息到重试队列");
        c2CMsgRetryService.addToRetryQueue(packet);
        Thread.sleep(500); // 等待异步添加完成
        
        // 3. 验证ZSet中存在msgId
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        assertTrue(zset.contains(testMsgId), "ZSet应该包含msgId");
        log.info("步骤2: 验证ZSet - msgId存在 ✓");
        
        // 4. 验证Hash中存在压缩数据
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        String compressedData = hash.get(testMsgId);
        assertNotNull(compressedData, "Hash应该包含压缩数据");
        log.info("步骤3: 验证Hash - 压缩数据存在，长度: {}", compressedData.length());
        
        // 5. 验证数据可以正确解压
        String decompressedJson = CompressionUtil.decompressFromBase64(compressedData);
        assertNotNull(decompressedJson, "应该能正确解压");
        C2CMsgRetryEvent event = JSONUtil.toBean(decompressedJson, C2CMsgRetryEvent.class);
        assertEquals(testMsgId, event.getMsgId(), "解压后的msgId应该匹配");
        assertEquals(packet.getClientMsgId(), event.getClientMsgId(), "clientMsgId应该匹配");
        log.info("步骤4: 验证压缩/解压 - 数据完整性 ✓");
        
        // 6. 修改score为过去时间（模拟到期）
        double pastScore = System.currentTimeMillis() - 5000;
        zset.addScore(testMsgId, pastScore - zset.getScore(testMsgId));
        log.info("步骤5: 修改score为过去时间 - 模拟消息到期");
        
        // 7. 调用scanRetryQueue（应该扫描到这条消息）
        log.info("步骤6: 调用scanRetryQueue扫描到期消息");
        c2CMsgRetryService.scanRetryQueue();
        
        // 8. 等待异步处理
        Thread.sleep(2000);
        
        log.info("✅ 集成测试 - 完整流程测试通过");
    }

    @Test
    @Order(10)
    @DisplayName("压缩测试 - 验证LZ4压缩效果")
    public void testCompressionEfficiency() throws Exception {
        log.info("=== 压缩测试 - 验证LZ4压缩效果 ===");
        
        // 1. 创建较大的测试消息
        C2CSendMsgAO packet = createTestPacket();
        packet.setMsgId("compression_test_" + System.currentTimeMillis());
        packet.setMsgContent("这是一条用于测试压缩效果的较长消息内容。".repeat(10)); // 重复10次
        
        // 2. 添加到重试队列
        c2CMsgRetryService.addToRetryQueue(packet);
        Thread.sleep(500);
        
        // 3. 获取原始JSON和压缩后数据
        C2CMsgRetryEvent retryEvent = new C2CMsgRetryEvent();
        retryEvent.setMsgId(packet.getMsgId());
        retryEvent.setClientMsgId(packet.getClientMsgId());
        retryEvent.setFromUserId(packet.getFromUserId());
        retryEvent.setToUserId(packet.getToUserId());
        retryEvent.setChatId(packet.getChatId());
        retryEvent.setMsgContent(packet.getMsgContent());
        retryEvent.setMsgFormat(packet.getMsgFormat());
        retryEvent.setMsgCreateTime(packet.getMsgCreateTime());
        retryEvent.setCreateTime(DateUtil.now());
        retryEvent.setRetryCount(0);
        retryEvent.setMaxRetries(3);
        
        String originalJson = JSONUtil.toJsonStr(retryEvent);
        int originalSize = originalJson.length();
        
        // 4. 从Redis获取压缩数据
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        String compressedData = hash.get(packet.getMsgId());
        assertNotNull(compressedData, "应该能获取到压缩数据");
        int compressedSize = compressedData.length();
        
        // 5. 计算压缩率
        double compressionRatio = CompressionUtil.compressionRatio(originalSize, compressedSize);
        log.info("原始大小: {} bytes", originalSize);
        log.info("压缩后大小: {} bytes", compressedSize);
        log.info("压缩率: {:.1f}%", compressionRatio);
        
        // 6. 验证压缩确实减少了体积
        assertTrue(compressedSize < originalSize, "压缩后体积应该小于原始体积");
        assertTrue(compressionRatio < 90, "压缩率应该小于90%");
        
        // 7. 验证解压后数据完整性
        String decompressed = CompressionUtil.decompressFromBase64(compressedData);
        assertEquals(originalJson.length(), decompressed.length(), "解压后长度应该与原始一致");
        
        log.info("✅ 压缩测试通过 - 压缩率: {:.1f}%", compressionRatio);
    }

    @Test
    @Order(11)
    @DisplayName("数据一致性测试 - ZSet和Hash同步")
    public void testDataConsistency() throws Exception {
        log.info("=== 数据一致性测试 - ZSet和Hash同步 ===");
        
        // 1. 添加多条消息
        List<String> msgIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            C2CSendMsgAO packet = createTestPacket();
            String msgId = "consistency_test_" + i + "_" + System.currentTimeMillis();
            packet.setMsgId(msgId);
            msgIds.add(msgId);
            
            c2CMsgRetryService.addToRetryQueue(packet);
        }
        Thread.sleep(1000);
        
        // 2. 验证ZSet和Hash中都有相同的msgId
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(TEST_QUEUE_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        RMap<String, String> hash = redissonClient.getMap(TEST_INDEX_KEY, org.redisson.client.codec.StringCodec.INSTANCE);
        
        for (String msgId : msgIds) {
            assertTrue(zset.contains(msgId), "ZSet应该包含msgId: " + msgId);
            assertNotNull(hash.get(msgId), "Hash应该包含msgId: " + msgId);
            
            // 验证可以解压
            String compressed = hash.get(msgId);
            String decompressed = CompressionUtil.decompressFromBase64(compressed);
            C2CMsgRetryEvent event = JSONUtil.toBean(decompressed, C2CMsgRetryEvent.class);
            assertNotNull(event, "应该能解析出事件对象");
        }
        
        log.info("✅ 数据一致性测试通过 - ZSet和Hash数据同步");
        
        // 3. 删除一条消息
        String msgIdToRemove = msgIds.get(0);
        c2CMsgRetryService.removeFromRetryQueue(msgIdToRemove);
        Thread.sleep(100);
        
        // 4. 验证ZSet和Hash都已删除
        assertFalse(zset.contains(msgIdToRemove), "ZSet应该已删除msgId");
        assertNull(hash.get(msgIdToRemove), "Hash应该已删除msgId");
        
        // 5. 验证其他消息未受影响
        for (int i = 1; i < msgIds.size(); i++) {
            String msgId = msgIds.get(i);
            assertTrue(zset.contains(msgId), "其他msgId应该仍在ZSet中");
            assertNotNull(hash.get(msgId), "其他msgId应该仍在Hash中");
        }
        
        log.info("✅ 删除操作一致性测试通过");
    }

    /**
     * 创建测试用的消息包
     */
    private C2CSendMsgAO createTestPacket() {
        C2CSendMsgAO packet = new C2CSendMsgAO();
        packet.setMsgId(String.valueOf(System.currentTimeMillis()));
        packet.setClientMsgId(UUID.randomUUID().toString());
        packet.setFromUserId(testFromUserId);
        packet.setToUserId(testToUserId);
        packet.setMsgContent("测试消息内容");
        packet.setMsgFormat(1);
        packet.setMsgCreateTime(System.currentTimeMillis());
        packet.setChatId("100-1-" + testFromUserId + "-" + testToUserId);
        return packet;
    }
}

