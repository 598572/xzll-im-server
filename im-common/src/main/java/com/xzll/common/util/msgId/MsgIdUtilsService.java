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
 * @Description: 基于雪花算法保证消息绝对唯一【数据中心暂时是集群名，workId 根据配置的策略来生成】，
 * 融入会话类型和uid使其信息【更丰富】
 * 增加本地序列号 保证 消息id 【严格自增】 暂不需要 雪花id中有序列号
 */
public class MsgIdUtilsService {

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


    public MsgIdUtilsService(Long workerId, String datacenterIdStr) {
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

    public MsgIdUtilsService() {
    }

    //集群名称转为数字
    private long generateDatacenterId(String groupName) {
        CRC32 crc = new CRC32();
        crc.update(groupName.getBytes(StandardCharsets.UTF_8));
        return crc.getValue() % (maxDatacenterId + 1);
    }


    private synchronized long nextId() {
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

    public List<String> generateBatchMessageId(long userId, boolean isGroupChat) {
        List<String> ids = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
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
        for (int i = 0; i < 1000; i++) {
            idPool.add(nextId());
        }
    }

    //============================== 提供给外部的方法 ==============================

    /**
     * 提供给外部，获取消息id的公共方法
     *
     * @param userId
     * @param isGroupChat
     * @return
     */
    public String generateMessageId(long userId, boolean isGroupChat) {
        //long id = nextId();
        long id = getNextIdFromPool();
        // 获取本地递增序列 雪花算法已经实现序列号 无需多此一举了
        //long localSeq = localSequence.incrementAndGet();
        Integer type = isGroupChat ? 2 : 1;
        return String.format("%d-%d-%d", type, userId, id);
    }

    /**
     * 获取一批消息id
     *
     * @param userId
     * @param isGroupChat
     * @return
     */
//    public Set<String> generateBatchMessageId(long userId, boolean isGroupChat) {
//        Set<String> sets = new HashSet<>();
//        for (int i = 0; i < 1000; i++) {
//            sets.add(generateMessageId(userId, isGroupChat));
//        }
//        return sets;
//    }

    /**
     * 提供给外部，从消息id 获取雪花id
     *
     * @param msgId
     * @return
     */
    public static Long getSnowflakeId(String msgId) {
        String snowflakeId = msgId.split("-")[2];
        return Long.valueOf(snowflakeId);
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


    public static void main(String[] args) {
        MsgIdUtilsService generator = new MsgIdUtilsService(2L, "xzll-im");

        //批量生成
        List<String> strings = generator.generateBatchMessageId(11L, false);
        System.out.println(strings);

        //单个生成
//        for (int i = 0; i < 10; i++) {
//            String msgId = generator.generateMessageId(12345, false);
//            System.out.println("生成的msgId:" + msgId);
//            MsgIdUtilsService parser = new MsgIdUtilsService();
//            Long snowflakeId = getSnowflakeId(msgId);
//            parser.parseId(snowflakeId);
//            System.out.println("\n");
//        }


    }

}
