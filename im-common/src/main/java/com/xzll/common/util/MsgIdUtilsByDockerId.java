package com.xzll.common.util;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author: hzz
 * @Date: 2024/6/16 09:06:04
 * @Description: 使用docker 容器id作为 workerId
 */
public class MsgIdUtilsByDockerId {
    private final long twepoch = 1288834974657L;
    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long sequenceBits = 12L;
    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    private final AtomicLong sequence = new AtomicLong(0);
    private final long workerId;
    private final long datacenterId;
    private long lastTimestamp = -1L;

    public MsgIdUtilsByDockerId(long datacenterId) {
        this.workerId = getWorkerIdFromContainerId();
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.datacenterId = datacenterId;
    }

    private long getWorkerIdFromContainerId() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/self/cgroup"));
            for (String line : lines) {
                if (line.contains("cpu")) {
                    String containerId = line.substring(line.lastIndexOf("/") + 1);
                    return Math.abs(containerId.hashCode()) % (maxWorkerId + 1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get workerId from Docker container ID", e);
        }
        throw new RuntimeException("No Docker container ID found");
    }

    public synchronized long nextId() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        }

        if (lastTimestamp == timestamp) {
            long seq = (sequence.incrementAndGet()) & sequenceMask;
            if (seq == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence.set(0);
        }

        lastTimestamp = timestamp;

        return ((timestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence.get();
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }

    public String generateMessageId(long userId, boolean isGroupChat) {
        long id = nextId();
        Integer type = isGroupChat ? 2 : 1;
        return String.format("%d-%d-%d", type, userId, id);
    }

    public static void main(String[] args) {
        MsgIdUtilsByDockerId generator = new MsgIdUtilsByDockerId(1);
        for (int i = 0; i < 10; i++) {
            System.out.println(generator.generateMessageId(12345, false));
        }
    }
}
