package com.xzll.console.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * @Author: hzz
 * @Date: 2025/8/27
 * @Description: HBase连接健康检查工具类
 */
@Slf4j
@Component
public class HBaseHealthChecker {

    @Resource
    private Connection hbaseConnection;

    /**
     * 检查HBase连接是否健康
     */
    public boolean isConnectionHealthy() {
        try (Admin admin = hbaseConnection.getAdmin()) {
            // 尝试获取集群状态
            admin.getClusterStatus();
            log.debug("HBase连接健康检查通过");
            return true;
        } catch (IOException e) {
            log.error("HBase连接健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查指定表是否存在
     */
    public boolean isTableExists(String tableName) {
        try (Admin admin = hbaseConnection.getAdmin()) {
            boolean exists = admin.tableExists(TableName.valueOf(tableName));
            log.debug("表 {} 存在状态: {}", tableName, exists);
            return exists;
        } catch (IOException e) {
            log.error("检查表 {} 是否存在时发生错误", tableName, e);
            return false;
        }
    }

    /**
     * 获取连接状态信息
     */
    public String getConnectionStatus() {
        if (hbaseConnection == null) {
            return "HBase连接未初始化";
        }
        
        if (hbaseConnection.isClosed()) {
            return "HBase连接已关闭";
        }
        
        if (isConnectionHealthy()) {
            return "HBase连接正常";
        } else {
            return "HBase连接异常";
        }
    }
} 