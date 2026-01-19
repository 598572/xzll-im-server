package com.xzll.business.service.impl;

import com.xzll.business.config.SearchSecurityConfig;
import com.xzll.business.service.SearchSecurityService;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 搜索安全服务实现
 */
@Service
@Slf4j
public class SearchSecurityServiceImpl implements SearchSecurityService {

    @Resource
    private SearchSecurityConfig searchSecurityConfig;

    @Resource
    private RedissonUtils redissonUtils;

    private static final String SEARCH_RATE_LIMIT_KEY_PREFIX = "search:rate:limit:";
    
    @Override
    public boolean checkSearchRateLimit(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }

        try {
            String key = SEARCH_RATE_LIMIT_KEY_PREFIX + userId;
            
            // 获取当前搜索次数
            String currentCountStr = redissonUtils.getString(key);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            
            // 检查是否超出限制
            if (currentCount >= searchSecurityConfig.getMaxSearchPerMinute()) {
                log.warn("用户搜索频率超限，用户ID:{}, 当前次数:{}, 限制:{}", 
                        userId, currentCount, searchSecurityConfig.getMaxSearchPerMinute());
                return false;
            }
            
            // 增加搜索次数
            redissonUtils.setString(key, String.valueOf(currentCount + 1), 60, TimeUnit.SECONDS);
            
            return true;
            
        } catch (Exception e) {
            log.error("检查搜索频率限制异常，用户ID:{}", userId, e);
            // 出现异常时，为了不影响正常功能，允许搜索
            return true;
        }
    }

    @Override
    public boolean containsSensitiveWord(String keyword) {
        if (!StringUtils.hasText(keyword) || !searchSecurityConfig.getEnableSensitiveWordFilter()) {
            return false;
        }

        try {
            String lowerKeyword = keyword.toLowerCase();
            
            for (String sensitiveWord : searchSecurityConfig.getSensitiveWords()) {
                if (lowerKeyword.contains(sensitiveWord.toLowerCase())) {
                    log.warn("搜索关键词包含敏感词，关键词:{}, 敏感词:{}", keyword, sensitiveWord);
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("检查敏感词异常，关键词:{}", keyword, e);
            // 出现异常时，为了安全起见，认为包含敏感词
            return true;
        }
    }

    @Override
    public void logSearch(String userId, String keyword, Integer resultCount) {
        if (!searchSecurityConfig.getEnableSearchLog()) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            log.info("用户搜索记录 - 时间:{}, 用户ID:{}, 关键词:{}, 结果数量:{}", 
                    timestamp, userId, keyword, resultCount);
            
            // 这里可以扩展为将搜索日志存储到数据库或其他持久化存储中
            
        } catch (Exception e) {
            log.error("记录搜索日志异常，用户ID:{}, 关键词:{}", userId, keyword, e);
        }
    }
}
