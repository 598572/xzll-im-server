package com.xzll.common.util.msgId.impl;

import com.xzll.common.util.msgId.WorkerIdStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @Author: hzz
 * @Date: 2024/6/16 11:14:47
 * @Description:
 */
@Component(value = "dockerWorkerIdStrategy")
public class DockerWorkerIdStrategy implements WorkerIdStrategy {

    @Override
    public long getWorkerId() {
        // 获取 Docker 容器 ID 的后几位作为 workerId
        String dockerId = getDockerId();
        return Long.parseLong(dockerId.substring(dockerId.length() - 5), 16) % 32; // 假设 workerId 的范围是0-31
    }

    private String getDockerId() {
        // 从 Docker 容器文件中读取容器 ID
        try {
            return new String(Files.readAllBytes(Paths.get("/proc/self/cgroup"))).split("/")[1].substring(0, 12);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get Docker ID", e);
        }
    }

}
