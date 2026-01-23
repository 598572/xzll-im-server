package com.xzll.console.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 健康检查结果VO
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
public class HealthCheckVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 检查时间戳
     */
    private Long timestamp;

    /**
     * 总体是否健康
     */
    private Boolean allHealthy;

    /**
     * HBase 健康状态
     */
    private StorageHealth hbase;

    /**
     * Elasticsearch 健康状态
     */
    private StorageHealth elasticsearch;

    /**
     * 存储健康状态
     */
    @Data
    public static class StorageHealth implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 是否健康
         */
        private Boolean healthy;

        /**
         * 状态描述
         */
        private String status;

        /**
         * 表/索引是否存在
         */
        private Boolean tableExists;

        /**
         * 索引信息（ES专用）
         */
        private Map<String, Object> indexInfo;
    }
}
