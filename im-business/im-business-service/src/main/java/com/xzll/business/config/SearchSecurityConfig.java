package com.xzll.business.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 用户搜索安全配置
 */
@Component
@ConfigurationProperties(prefix = "im.search.security")
@Data
public class SearchSecurityConfig {

    /**
     * 搜索频率限制（每分钟最大搜索次数）
     */
    private Integer maxSearchPerMinute = 30;

    /**
     * 搜索关键词最小长度
     */
    private Integer minKeywordLength = 2;

    /**
     * 搜索关键词最大长度
     */
    private Integer maxKeywordLength = 50;

    /**
     * 每页最大结果数量
     */
    private Integer maxPageSize = 50;

    /**
     * 是否启用敏感词过滤
     */
    private Boolean enableSensitiveWordFilter = true;

    /**
     * 敏感词列表
     */
    private List<String> sensitiveWords = Arrays.asList(
            "admin", "root", "system", "test", "管理员"
    );

    /**
     * 是否记录搜索日志
     */
    private Boolean enableSearchLog = true;

}
