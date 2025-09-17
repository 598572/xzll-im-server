package com.xzll.business.service;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 搜索安全服务接口
 */
public interface SearchSecurityService {

    /**
     * 检查搜索频率限制
     *
     * @param userId 用户ID
     * @return true-允许搜索，false-超出频率限制
     */
    boolean checkSearchRateLimit(String userId);

    /**
     * 检查关键词是否包含敏感词
     *
     * @param keyword 搜索关键词
     * @return true-包含敏感词，false-不包含敏感词
     */
    boolean containsSensitiveWord(String keyword);

    /**
     * 记录搜索日志
     *
     * @param userId   用户ID
     * @param keyword  搜索关键词
     * @param resultCount 搜索结果数量
     */
    void logSearch(String userId, String keyword, Integer resultCount);

}
