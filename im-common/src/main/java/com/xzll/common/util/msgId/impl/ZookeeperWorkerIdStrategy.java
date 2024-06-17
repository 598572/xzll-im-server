package com.xzll.common.util.msgId.impl;

import com.xzll.common.util.msgId.WorkerIdStrategy;
import org.springframework.stereotype.Component;

/**
 * @Author: hzz
 * @Date: 2024/6/16 11:16:15
 * @Description:
 */
@Component(value = "ZookeeperWorkerIdStrategy")
public class ZookeeperWorkerIdStrategy implements WorkerIdStrategy {


    @Override
    public long getWorkerId() {
        try {
            // 从 Zookeeper 节点中读取 workerId
            //byte[] data = client.getData().forPath("/workerId");
            //return Long.parseLong(new String(data)) % 32; // 假设 workerId 的范围是0-31
            return 111;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get workerId from Zookeeper", e);
        }
    }
}
