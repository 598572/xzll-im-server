package com.xzll.business.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HBase 连接健康检查工具类
 * @Author: hzz
 * @Date: 2025/08/27
 */
@Slf4j
@Component
public class HBaseHealthCheck {

    @Autowired(required = false)
    private Connection hbaseConnection;

    /**
     * 每5分钟检查一次HBase连接状态
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void checkHBaseConnection() {
        try {
            if (hbaseConnection != null && !hbaseConnection.isClosed()) {
                try (Admin admin = hbaseConnection.getAdmin()) {
                    // 尝试获取表列表来验证连接
                    admin.listTableNames();
                    log.debug("HBase连接状态正常");
                }
            } else {
                log.warn("HBase连接已关闭或为空");
            }
        } catch (IOException e) {
            log.error("HBase连接健康检查失败: {}", e.getMessage());
        }
    }

    /**
     * 手动检查HBase连接状态
     * @return 连接是否正常
     */
    public boolean isConnectionHealthy() {
        try {
            if (hbaseConnection != null && !hbaseConnection.isClosed()) {
                try (Admin admin = hbaseConnection.getAdmin()) {
                    admin.listTableNames();
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            log.error("HBase连接状态检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取连接状态信息
     * @return 连接状态描述
     */
    public String getConnectionStatus() {
        if (hbaseConnection == null) {
            return "连接对象为空";
        }
        if (hbaseConnection.isClosed()) {
            return "连接已关闭";
        }
        if (isConnectionHealthy()) {
            return "连接正常";
        } else {
            return "连接异常";
        }
    }
} 