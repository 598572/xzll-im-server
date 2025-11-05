package com.xzll.common.util.msgId;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * @Author: hzz
 * @Date: 2024/6/16 08:46:17
 * @Description: 基于雪花算法的消息ID生成服务
 * 
 * 【版本升级说明 - 2025/10/30】
 * - 新增简化版消息ID生成：只使用雪花算法ID，去掉会话类型和用户ID
 * - 优势：ID更短、HBase RowKey更高效、减少存储开销
 * - 推荐使用：generateSimpleMessageId() 替代 generateMessageId()
 * - 旧方法已标记为 @Deprecated 但保持兼容
 * 
 * 技术特点：
 * - 数据中心ID：基于集群名生成
 * - 工作节点ID：根据配置策略生成（MAC地址、Docker ID等）  
 * - ID池机制：批量生成提升性能
 * - 严格自增：雪花算法保证时间有序
 */
public class SnowflakeIdService {

    public static final int ONCE_BATCH_COUNT = 1000;
    // 定义 Snowflake 算法的参数
    private final long twepoch = 1288834974657L; // 自定义的时间戳起点
    private final long workerIdBits = 5L;       // 机器 ID 所占的位数
    private final long datacenterIdBits = 5L;   // 数据中心 ID 所占的位数
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long sequenceBits = 12L;      // 序列所占的位数
    private final long workerIdShift = sequenceBits;// 12
    private final long datacenterIdShift = sequenceBits + workerIdBits;//// 17
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;// 22
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);// 4095

    private final AtomicLong sequence = new AtomicLong(0);
    private long workerId;
    private long datacenterId;
    private long lastTimestamp = -1L;


    // 本地递增序列，用于确保严格递增
    //private final AtomicLong localSequence = new AtomicLong(0);

    // 缓存池
    private final List<Long> idPool = new ArrayList<>();

    private final AtomicInteger poolIndex = new AtomicInteger(0);


    public SnowflakeIdService(Long workerId, String datacenterIdStr) {
        //workId
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        this.workerId = workerId;
        //根据集群名 生成数据中心id
        long datacenterId = generateDatacenterId(datacenterIdStr);
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.datacenterId = datacenterId;
    }

    public SnowflakeIdService() {
        this.workerId = 1L;
        //根据集群名 生成数据中心id
        long datacenterId = generateDatacenterId("common");
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.datacenterId = datacenterId;
    }

//    public MsgIdUtilsService() {
//    }

    //集群名称转为数字
    private long generateDatacenterId(String groupName) {
        CRC32 crc = new CRC32();
        crc.update(groupName.getBytes(StandardCharsets.UTF_8));
        return crc.getValue() % (maxDatacenterId + 1);
    }


    public synchronized long nextId() {
        long timestamp = timeGen();

        //如果当前时间戳比上一次生成ID的时间戳小，说明系统时钟出现了回拨，抛出异常以避免生成重复ID
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        }

        //如果当前时间戳与上一次生成ID的时间戳相同，说明是在同一毫秒内生成多个ID

        if (lastTimestamp == timestamp) {
            //sequence.incrementAndGet() 用于获取并自增序列号，并通过 sequenceMask 掩码保证序列号不超过最大值（4095）
            long seq = (sequence.incrementAndGet()) & sequenceMask;
            if (seq == 0) {
                //如果序列号达到最大值，进入下一毫秒（通过 tilNextMillis(lastTimestamp) 方法），以确保在同一毫秒内不会生成重复的序列号
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            //如果时间戳不同，重置序列号为0
            sequence.set(0);
        }
        //更新最后生成ID的时间戳
        lastTimestamp = timestamp;
        //生成唯一ID
        return ((timestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence.get();
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }


    // 添加的解析时间戳的方法
    private String parseTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date(timestamp));
    }

    /**
     * 【已废弃】批量生成消息ID（旧格式）
     * 
     * @deprecated 使用 generateBatchSimpleMessageId(int count) 替代
     * @param userId 用户ID（已不需要）
     * @param isGroupChat 是否群聊（已不需要）
     * @return 旧格式消息ID列表
     */
    @Deprecated
    public List<String> generateBatchMessageId(long userId, boolean isGroupChat) {
        List<String> ids = new ArrayList<>(ONCE_BATCH_COUNT);
        for (int i = 0; i < ONCE_BATCH_COUNT; i++) {
            ids.add(generateMessageId(userId, isGroupChat));
        }
        return ids;
    }

    private long getNextIdFromPool() {
        synchronized (idPool) {
            if (poolIndex.get() >= idPool.size()) {
                refillIdPool();
            }
            return idPool.get(poolIndex.getAndIncrement());
        }
    }

    private void refillIdPool() {
        idPool.clear();
        poolIndex.set(0);
        for (int i = 0; i < ONCE_BATCH_COUNT; i++) {
            idPool.add(nextId());
        }
    }

    //============================== 提供给外部的方法 ==============================

    /**
     * 【推荐】生成简化的消息ID - 只使用雪花算法ID
     * 优势：ID更短、性能更好、减少HBase RowKey长度
     * 
     * @return 纯雪花算法ID字符串
     */
    public String generateSimpleMessageId() {
        long id = getNextIdFromPool();
        return String.valueOf(id);
    }

    /**
     * 批量生成简化的消息ID
     * 
     * @param count 生成数量
     * @return 消息ID列表
     */
    public List<String> generateBatchSimpleMessageId(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(generateSimpleMessageId());
        }
        return ids;
    }

    /**
     * 【已废弃】提供给外部，获取消息id的公共方法
     * 
     * @deprecated 使用 generateSimpleMessageId() 替代，新版本只使用雪花算法ID
     * @param userId 用户ID（已不需要）
     * @param isGroupChat 是否群聊（已不需要）
     * @return 格式：type-userId-snowflakeId
     */
    @Deprecated
    public String generateMessageId(long userId, boolean isGroupChat) {
        long id = getNextIdFromPool();
        // 获取本地递增序列 雪花算法已经实现序列号 无需多此一举了
        //long localSeq = localSequence.incrementAndGet();
        Integer type = isGroupChat ? 2 : 1;
        return String.format("%d-%d-%d", type, userId, id);
    }

    /**
     * 【推荐】从简化消息ID获取雪花ID（新格式专用）
     * 适用于 generateSimpleMessageId() 生成的ID
     * 
     * @param msgId 简化格式的消息ID（纯数字字符串）
     * @return 雪花算法ID
     */
    public static Long getSnowflakeIdFromSimple(String msgId) {
        try {
            return Long.valueOf(msgId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid simple message ID format: " + msgId, e);
        }
    }

    /**
     * 【已废弃】提供给外部，从消息id 获取雪花id（兼容旧格式）
     * 适用于旧格式：type-userId-snowflakeId
     *
     * @deprecated 使用 getSnowflakeIdFromSimple(String msgId) 替代
     * @param msgId 旧格式的消息ID
     * @return 雪花算法ID
     */
    @Deprecated
    public static Long getSnowflakeId(String msgId) {
        try {
            // 先尝试新格式（纯数字）
            return Long.valueOf(msgId);
        } catch (NumberFormatException e) {
            // 回退到旧格式解析
            String[] parts = msgId.split("-");
            if (parts.length >= 3) {
                return Long.valueOf(parts[2]);
            }
            throw new IllegalArgumentException("Invalid message ID format: " + msgId, e);
        }
    }

    /**
     * 提供给外部，反解析雪花id
     *
     * @param id
     */
    public void parseId(long id) {
        long timestamp = (id >> timestampLeftShift) + twepoch;
        long datacenterId = (id >> datacenterIdShift) & ((1L << datacenterIdBits) - 1);
        long workerId = (id >> workerIdShift) & ((1L << workerIdBits) - 1);
        long sequence = id & sequenceMask;

        System.out.println("Timestamp: " + timestamp + " (" + parseTimestamp(timestamp) + ")");
        System.out.println("Datacenter ID: " + datacenterId);
        System.out.println("Worker ID: " + workerId);
        System.out.println("Sequence: " + sequence);
    }

    //============================== 压缩雪花算法 - 用户ID专用 ==============================
    
    // 压缩雪花算法参数（用户ID专用）
    private static final long USER_ID_START_TIME = 1640995200L; // 2022-01-01 00:00:00 秒时间戳
    private static final long USER_MACHINE_ID_BITS = 4L;        // 4位机器ID，最多16台机器
    private static final long USER_SEQUENCE_BITS = 6L;          // 6位序列号，每秒最多64个ID
    
    private static final long MAX_USER_MACHINE_ID = (1L << USER_MACHINE_ID_BITS) - 1;  // 15
    private static final long MAX_USER_SEQUENCE = (1L << USER_SEQUENCE_BITS) - 1;      // 63
    private static final long USER_MACHINE_SHIFT = USER_SEQUENCE_BITS;                  // 6
    private static final long USER_TIMESTAMP_SHIFT = USER_SEQUENCE_BITS + USER_MACHINE_ID_BITS; // 10
    
    // 用户ID生成专用变量
    private long userIdLastTimestamp = -1L;
    private long userIdSequence = 0L;
    private final long userMachineId;
    
    // 初始化用户机器ID（在构造函数中设置）
    {
        // 基于现有的datacenterId和workerId生成用户机器ID
        userMachineId = (datacenterId + workerId) % (MAX_USER_MACHINE_ID + 1);
    }

    /**
     * 【推荐】生成压缩雪花算法用户ID - 专为用户ID优化
     * 
     * 特点：
     * - ID长度：10-12位数字（比原版减少约40%）
     * - 时间范围：136年（2022-2158年）
     * - 保留时间特性：可解析注册时间
     * - 高性能：每秒可生成64个唯一ID
     * 
     * @return 压缩格式的用户ID字符串
     */
    public synchronized String generateCompactUserId() {
        long timestamp = System.currentTimeMillis() / 1000 - USER_ID_START_TIME;
        
        // 检查时钟回拨
        if (timestamp < userIdLastTimestamp) {
            throw new RuntimeException("用户ID生成失败：检测到时钟回拨，当前时间=" + timestamp + 
                                     ", 上次时间=" + userIdLastTimestamp);
        }
        
        // 处理同一秒内的序列号
        if (timestamp == userIdLastTimestamp) {
            userIdSequence = (userIdSequence + 1) & MAX_USER_SEQUENCE;
            if (userIdSequence == 0) {
                // 序列号用完，等待下一秒
                timestamp = waitForNextSecond(userIdLastTimestamp);
            }
        } else {
            // 新的一秒，重置序列号
            userIdSequence = 0L;
        }
        
        userIdLastTimestamp = timestamp;
        
        // 组装压缩ID：[32位时间戳][4位机器ID][6位序列号] = 42位
        long compactId = (timestamp << USER_TIMESTAMP_SHIFT) | 
                        (userMachineId << USER_MACHINE_SHIFT) | 
                        userIdSequence;
        
        return String.valueOf(compactId);
    }

    /**
     * 批量生成压缩用户ID
     * 
     * @param count 生成数量
     * @return 用户ID列表
     */
    public List<String> generateBatchCompactUserId(int count) {
        List<String> userIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            userIds.add(generateCompactUserId());
        }
        return userIds;
    }

    /**
     * 从压缩用户ID中提取注册时间
     * 
     * @param compactUserId 压缩格式的用户ID
     * @return 用户注册时间的毫秒时间戳
     */
    public static long extractUserRegistrationTime(String compactUserId) {
        try {
            long id = Long.parseLong(compactUserId);
            long timestamp = id >> USER_TIMESTAMP_SHIFT;
            return (timestamp + USER_ID_START_TIME) * 1000; // 转为毫秒时间戳
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的压缩用户ID格式: " + compactUserId, e);
        }
    }

    /**
     * 从压缩用户ID中提取机器ID
     * 
     * @param compactUserId 压缩格式的用户ID  
     * @return 机器ID
     */
    public static long extractMachineId(String compactUserId) {
        try {
            long id = Long.parseLong(compactUserId);
            return (id >> USER_MACHINE_SHIFT) & MAX_USER_MACHINE_ID;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的压缩用户ID格式: " + compactUserId, e);
        }
    }

    /**
     * 从压缩用户ID中提取序列号
     * 
     * @param compactUserId 压缩格式的用户ID
     * @return 序列号
     */
    public static long extractSequence(String compactUserId) {
        try {
            long id = Long.parseLong(compactUserId);
            return id & MAX_USER_SEQUENCE;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的压缩用户ID格式: " + compactUserId, e);
        }
    }

    /**
     * 解析压缩用户ID的详细信息
     * 
     * @param compactUserId 压缩格式的用户ID
     */
    public static void parseCompactUserId(String compactUserId) {
        try {
            long id = Long.parseLong(compactUserId);
            long timestamp = id >> USER_TIMESTAMP_SHIFT;
            long machineId = (id >> USER_MACHINE_SHIFT) & MAX_USER_MACHINE_ID;
            long sequence = id & MAX_USER_SEQUENCE;
            
            long registrationTime = (timestamp + USER_ID_START_TIME) * 1000;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            System.out.println("========== 压缩用户ID解析结果 ==========");
            System.out.println("用户ID: " + compactUserId);
            System.out.println("注册时间: " + sdf.format(new Date(registrationTime)));
            System.out.println("机器ID: " + machineId);
            System.out.println("序列号: " + sequence);
            System.out.println("时间戳: " + timestamp + " (秒)");
            System.out.println("======================================");
        } catch (NumberFormatException e) {
            System.err.println("解析失败：无效的压缩用户ID格式 - " + compactUserId);
        }
    }

    /**
     * 检查压缩用户ID的有效性
     * 
     * @param compactUserId 压缩格式的用户ID
     * @return 是否有效
     */
    public static boolean isValidCompactUserId(String compactUserId) {
        try {
            long id = Long.parseLong(compactUserId);
            long timestamp = id >> USER_TIMESTAMP_SHIFT;
            long machineId = (id >> USER_MACHINE_SHIFT) & MAX_USER_MACHINE_ID;
            long sequence = id & MAX_USER_SEQUENCE;
            
            // 检查各部分是否在有效范围内
            return timestamp > 0 && 
                   machineId >= 0 && machineId <= MAX_USER_MACHINE_ID &&
                   sequence >= 0 && sequence <= MAX_USER_SEQUENCE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 等待下一秒
     */
    private long waitForNextSecond(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() / 1000 - USER_ID_START_TIME;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() / 1000 - USER_ID_START_TIME;
        }
        return timestamp;
    }

    /**
     * 获取压缩雪花算法的统计信息
     * 
     * @return 统计信息字符串
     */
    public String getCompactUserIdStats() {
        long currentTime = System.currentTimeMillis() / 1000;
        long usedYears = (long)((currentTime - USER_ID_START_TIME) / (365.25 * 24 * 3600));
        long remainingYears = 136 - usedYears;
        
        return String.format(
            "压缩用户ID统计信息:\n" +
            "- 算法类型: 压缩雪花算法\n" +
            "- ID位数: 42位 (约10-12位数字)\n" +
            "- 时间精度: 秒级\n" +
            "- 机器ID: %d (最大15)\n" +
            "- 已运行: %d年\n" +
            "- 剩余可用: %d年\n" +
            "- 每秒容量: 64个ID\n" +
            "- 到期时间: 约2158年",
            userMachineId, usedYears, remainingYears
        );
    }

    /**
     * 根据时间戳生成对应的消息ID边界（用于HBase RowKey范围查询）
     * 
     * @param timestamp 毫秒级时间戳
     * @param isUpperBound 是否为上边界（true=最大值，false=最小值）
     * @return 对应的消息ID字符串
     */
    public static String generateMsgIdBoundaryFromTimestamp(Long timestamp, boolean isUpperBound) {
        if (timestamp == null) {
            return "";
        }
        
        try {
            // 注意：消息ID使用的是秒级精度的压缩雪花算法
            // 输入是毫秒级时间戳，需要转换为秒级，然后减去起点时间
            long compressedTimestamp = timestamp / 1000 - USER_ID_START_TIME;
            
            // 构造边界msgId
            long boundaryMsgId;
            if (isUpperBound) {
                // 上边界：该秒内的最大msgId（使用最大机器ID和最大序列号）
                // 这确保包含该秒的所有消息
                boundaryMsgId = (compressedTimestamp << USER_TIMESTAMP_SHIFT) | 
                               (MAX_USER_MACHINE_ID << USER_MACHINE_SHIFT) | 
                               MAX_USER_SEQUENCE;
            } else {
                // 下边界：该秒内的最小msgId（使用最小机器ID和最小序列号）
                // 这确保从该秒的第一条消息开始
                boundaryMsgId = compressedTimestamp << USER_TIMESTAMP_SHIFT;
            }
            
            return String.valueOf(boundaryMsgId);
        } catch (Exception e) {
            throw new IllegalArgumentException("时间戳转换msgId边界失败: timestamp=" + timestamp, e);
        }
    }

    /**
     * 根据时间戳生成下边界消息ID（用于范围查询的起始点）
     * 
     * @param timestamp 毫秒级时间戳
     * @return 对应的最小消息ID
     */
    public static String generateMsgIdLowerBound(Long timestamp) {
        return generateMsgIdBoundaryFromTimestamp(timestamp, false);
    }

    /**
     * 根据时间戳生成上边界消息ID（用于范围查询的结束点）
     * 
     * @param timestamp 毫秒级时间戳
     * @return 对应的最大消息ID
     */
    public static String generateMsgIdUpperBound(Long timestamp) {
        return generateMsgIdBoundaryFromTimestamp(timestamp, true);
    }


    public static void main(String[] args) {
        SnowflakeIdService generator = new SnowflakeIdService(2L, "xzll-im");

        //批量生成
        List<String> strings = generator.generateBatchMessageId(11L, false);
        System.out.println(strings);

//        单个生成
        for (int i = 0; i < 10; i++) {
            String msgId = generator.generateMessageId(12345, false);
            System.out.println("生成的msgId:" + msgId);
            SnowflakeIdService parser = new SnowflakeIdService();
            Long snowflakeId = getSnowflakeId(msgId);
            parser.parseId(snowflakeId);
            System.out.println("\n");
        }

        SnowflakeIdService msgIdUtilsService = new SnowflakeIdService();
        for (int i = 0; i <100; i++) {
            System.out.println(msgIdUtilsService.nextId());
        }


    }

}
