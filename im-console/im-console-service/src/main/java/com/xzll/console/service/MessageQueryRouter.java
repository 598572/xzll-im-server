package com.xzll.console.service;

import cn.hutool.core.util.StrUtil;
import com.xzll.console.config.nacos.ElasticSearchNacosConfig;
import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.vo.MessageSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 消息查询路由服务（智能路由版本）
 * 根据查询条件和配置自动选择最优数据源
 *
 * 路由策略:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  查询条件       │  ES开启  │  ES关闭                             │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  有chatId      │  MongoDB │  MongoDB（分片键，性能最优）         │
 * │  无chatId      │  ES      │  MongoDB（跨分片查询，性能较差）     │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 路由说明：
 * 1. 有chatId：走MongoDB（分片键，单分片查询）
 * 2. 无chatId + ES开启：走ES（避免MongoDB跨分片scatter-gather）
 * 3. 无chatId + ES关闭：走MongoDB（兜底）
 * 4. 一次查询只使用一种数据源，不会混合查询
 * 5. 支持运行时动态切换（通过Nacos配置刷新）
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
public class MessageQueryRouter {

    @Resource
    private MessageMongoQueryService mongoQueryService;

    @Resource
    private MessageESQueryService esQueryService;

    @Resource(name = "elasticSearchNacosConfig")
    private ElasticSearchNacosConfig elasticSearchConfig;

    /**
     * 数据来源标识
     */
    public static final String SOURCE_MONGODB = "MongoDB";
    public static final String SOURCE_ES = "ES";

    /**
     * 判断是否启用ES
     */
    private boolean isESEnabled() {
        Boolean enabled = elasticSearchConfig.getSyncEnabled();
        return enabled != null && enabled;
    }

    /**
     * 智能路由查询（核心逻辑）
     * 根据查询条件自动选择最优数据源
     *
     * 路由规则：
     * 1. 有chatId -> MongoDB（分片键，性能最优）
     * 2. 无chatId + ES开启 -> ES（避免MongoDB跨分片查询）
     * 3. 无chatId + ES关闭 -> MongoDB（兜底）
     *
     * @param searchDTO 搜索条件
     * @return 搜索结果
     */
    public MessageSearchResultVO smartSearch(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();
        boolean hasChatId = StrUtil.isNotBlank(searchDTO.getChatId());

        // 智能路由决策
        String dataSource;
        MessageSearchResultVO result;

        if (hasChatId) {
            // 有chatId：走MongoDB（分片键，单分片查询，性能最优）
            log.info("智能路由: 有chatId({}) -> MongoDB（分片键查询）", searchDTO.getChatId());
            result = mongoQueryService.search(searchDTO);
            dataSource = SOURCE_MONGODB;
        } else if (isESEnabled()) {
            // 无chatId + ES开启：走ES（避免MongoDB跨分片scatter-gather）
            log.info("智能路由: 无chatId + ES开启 -> ES（避免跨分片查询）");
            result = esQueryService.search(searchDTO);
            dataSource = SOURCE_ES;
        } else {
            // 无chatId + ES关闭：走MongoDB（兜底，会有性能警告）
            log.warn("智能路由: 无chatId + ES关闭 -> MongoDB（跨分片查询，性能较差）");
            result = mongoQueryService.search(searchDTO);
            dataSource = SOURCE_MONGODB;
        }

        result.setDataSource(dataSource);
        result.setCostMs(System.currentTimeMillis() - startTime);

        log.info("查询完成: 数据源={}, 条件={}, 耗时={}ms",
                dataSource, summarizeConditions(searchDTO), result.getCostMs());

        return result;
    }

    /**
     * 按 chatId 查询（有chatId，直接走MongoDB）
     *
     * @param chatId   会话ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByChatId(String chatId, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();

        log.info("按chatId查询 -> MongoDB（分片键）: chatId={}", chatId);
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setChatId(chatId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);

        MessageSearchResultVO result = mongoQueryService.search(dto);
        result.setDataSource(SOURCE_MONGODB);
        result.setCostMs(System.currentTimeMillis() - startTime);

        return result;
    }

    /**
     * 按用户ID查询（发送方或接收方）- 无chatId，根据ES配置智能路由
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByUserId(String userId, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();

        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setFromUserId(userId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);

        // 无chatId，根据ES配置选择
        if (isESEnabled()) {
            log.info("按用户ID查询 -> ES: userId={}", userId);
            MessageSearchResultVO result = esQueryService.searchByFromUserId(userId, pageNum, pageSize);
            result.setDataSource(SOURCE_ES);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        } else {
            log.warn("按用户ID查询 -> MongoDB（跨分片）: userId={}", userId);
            MessageSearchResultVO result = mongoQueryService.search(dto);
            result.setDataSource(SOURCE_MONGODB);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 消息内容搜索 - 无chatId，根据ES配置智能路由
     *
     * @param content  搜索关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();

        // 无chatId，根据ES配置选择
        if (isESEnabled()) {
            log.info("内容搜索 -> ES: content={}", content);
            MessageSearchResultVO result = esQueryService.searchByContent(content, pageNum, pageSize);
            result.setDataSource(SOURCE_ES);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        } else {
            log.warn("内容搜索 -> MongoDB（跨分片）: content={}", content);
            MessageSearchResultVO result = mongoQueryService.searchByContent(content, pageNum, pageSize);
            result.setDataSource(SOURCE_MONGODB);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 获取最新消息 - 无chatId，根据ES配置智能路由
     *
     * @param limit 数量限制
     * @return 消息列表
     */
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        if (isESEnabled()) {
            log.info("获取最新消息 -> ES: limit={}", limit);
            return esQueryService.getLatestMessages(limit);
        } else {
            log.warn("获取最新消息 -> MongoDB（跨分片）: limit={}", limit);
            return mongoQueryService.getLatestMessages(limit);
        }
    }

    /**
     * 获取消息统计信息 - 无chatId，根据ES配置智能路由
     */
    public java.util.Map<String, Object> getMessageStatistics() {
        if (isESEnabled()) {
            log.info("获取消息统计 -> ES");
            java.util.Map<String, Object> stats = esQueryService.getMessageStatistics();
            stats.put("dataSource", SOURCE_ES);
            return stats;
        } else {
            log.warn("获取消息统计 -> MongoDB（跨分片）");
            java.util.Map<String, Object> stats = mongoQueryService.getMessageStatistics();
            stats.put("dataSource", SOURCE_MONGODB);
            return stats;
        }
    }

    /**
     * 获取用户消息统计 - 无chatId，根据ES配置智能路由
     */
    public java.util.Map<String, Object> getUserMessageStatistics(String userId) {
        if (isESEnabled()) {
            log.info("获取用户消息统计 -> ES: userId={}", userId);
            java.util.Map<String, Object> stats = esQueryService.getUserMessageStatistics(userId);
            stats.put("dataSource", SOURCE_ES);
            return stats;
        } else {
            log.warn("获取用户消息统计 -> MongoDB（跨分片）: userId={}", userId);
            java.util.Map<String, Object> stats = mongoQueryService.getUserMessageStatistics(userId);
            stats.put("dataSource", SOURCE_MONGODB);
            return stats;
        }
    }

    /**
     * 获取会话消息统计（有chatId，直接走MongoDB）
     */
    public java.util.Map<String, Object> getChatMessageStatistics(String chatId) {
        log.info("获取会话消息统计 -> MongoDB（分片键）: chatId={}", chatId);
        java.util.Map<String, Object> stats = mongoQueryService.getChatMessageStatistics(chatId);
        stats.put("dataSource", SOURCE_MONGODB);
        return stats;
    }

    /**
     * 获取今日消息数 - 无chatId，根据ES配置智能路由
     */
    public Long getTodayMessageCount() {
        if (isESEnabled()) {
            log.info("获取今日消息数 -> ES");
            return esQueryService.getTodayMessageCount();
        } else {
            log.warn("获取今日消息数 -> MongoDB（跨分片）");
            return mongoQueryService.getTodayMessageCount();
        }
    }

    /**
     * 获取消息趋势 - 无chatId，根据ES配置智能路由
     *
     * @param days 天数
     * @return Map<日期(MM-dd), 消息数>
     */
    public java.util.Map<String, Long> getMessagesTrend(int days) {
        if (isESEnabled()) {
            log.info("获取消息趋势 -> ES: days={}", days);
            return esQueryService.getMessagesTrend(days);
        } else {
            log.warn("获取消息趋势 -> MongoDB（跨分片）: days={}", days);
            return mongoQueryService.getMessagesTrend(days);
        }
    }

    /**
     * 获取健康状态
     */
    public boolean isHealthy() {
        // 根据配置检查对应的数据源
        if (isESEnabled()) {
            return esQueryService.isConnectionHealthy();
        } else {
            return mongoQueryService.isConnectionHealthy();
        }
    }

    /**
     * 获取当前使用的数据源
     */
    public String getCurrentDataSource() {
        return isESEnabled() ? SOURCE_ES : SOURCE_MONGODB;
    }

    /**
     * 条件摘要（用于日志）
     */
    private String summarizeConditions(MessageSearchDTO dto) {
        StringBuilder sb = new StringBuilder("[");
        if (StrUtil.isNotBlank(dto.getChatId())) sb.append("chatId,");
        if (StrUtil.isNotBlank(dto.getFromUserId())) sb.append("fromUserId,");
        if (StrUtil.isNotBlank(dto.getToUserId())) sb.append("toUserId,");
        if (StrUtil.isNotBlank(dto.getContent())) sb.append("content,");
        if (dto.getMsgStatus() != null) sb.append("msgStatus,");
        if (dto.getMsgFormat() != null) sb.append("msgFormat,");
        if (dto.getStartTime() != null) sb.append("startTime,");
        if (dto.getEndTime() != null) sb.append("endTime,");
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
