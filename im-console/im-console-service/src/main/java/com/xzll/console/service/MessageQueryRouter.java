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
 * 消息查询路由服务
 * 根据配置和查询条件智能选择最优存储进行查询
 *
 * 路由策略:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  配置项                        │  ES开启  │  ES关闭              │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  syncEnabled=true              │  使用ES   │  -                  │
 * │  syncEnabled=false             │  -       │  使用MongoDB         │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 注意：
 * 1. 一次查询只使用一种数据源，不会混合查询
 * 2. 支持运行时动态切换（通过Nacos配置刷新）
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
     * 智能路由查询
     * 根据 syncEnabled 配置选择数据源
     *
     * @param searchDTO 搜索条件
     * @return 搜索结果
     */
    public MessageSearchResultVO smartSearch(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();

        // 根据配置选择数据源
        if (isESEnabled()) {
            log.info("查询路由决策: {} -> ES", summarizeConditions(searchDTO));
            MessageSearchResultVO result = esQueryService.search(searchDTO);
            result.setDataSource(SOURCE_ES);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        } else {
            log.info("查询路由决策: {} -> MongoDB", summarizeConditions(searchDTO));
            MessageSearchResultVO result = mongoQueryService.search(searchDTO);
            result.setDataSource(SOURCE_MONGODB);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 按 chatId 查询
     *
     * @param chatId   会话ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByChatId(String chatId, int pageNum, int pageSize) {
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setChatId(chatId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return smartSearch(dto);
    }

    /**
     * 按用户ID查询（发送方或接收方）
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByUserId(String userId, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();

        if (isESEnabled()) {
            log.info("按用户ID查询 -> ES: userId={}", userId);
            MessageSearchResultVO result = esQueryService.searchByFromUserId(userId, pageNum, pageSize);
            result.setDataSource(SOURCE_ES);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        } else {
            log.info("按用户ID查询 -> MongoDB: userId={}", userId);
            MessageSearchDTO dto = new MessageSearchDTO();
            dto.setFromUserId(userId);
            dto.setPageNum(pageNum);
            dto.setPageSize(pageSize);
            MessageSearchResultVO result = mongoQueryService.search(dto);
            result.setDataSource(SOURCE_MONGODB);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 消息内容搜索
     *
     * @param content  搜索关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();

        if (isESEnabled()) {
            log.info("内容搜索 -> ES: content={}", content);
            MessageSearchResultVO result = esQueryService.searchByContent(content, pageNum, pageSize);
            result.setDataSource(SOURCE_ES);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        } else {
            log.info("内容搜索 -> MongoDB: content={}", content);
            MessageSearchResultVO result = mongoQueryService.searchByContent(content, pageNum, pageSize);
            result.setDataSource(SOURCE_MONGODB);
            result.setCostMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 获取最新消息
     *
     * @param limit 数量限制
     * @return 消息列表
     */
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        if (isESEnabled()) {
            log.info("获取最新消息 -> ES: limit={}", limit);
            return esQueryService.getLatestMessages(limit);
        } else {
            log.info("获取最新消息 -> MongoDB: limit={}", limit);
            return mongoQueryService.getLatestMessages(limit);
        }
    }

    /**
     * 获取消息统计信息
     */
    public java.util.Map<String, Object> getMessageStatistics() {
        if (isESEnabled()) {
            log.info("获取消息统计 -> ES");
            java.util.Map<String, Object> stats = esQueryService.getMessageStatistics();
            stats.put("dataSource", SOURCE_ES);
            return stats;
        } else {
            log.info("获取消息统计 -> MongoDB");
            java.util.Map<String, Object> stats = mongoQueryService.getMessageStatistics();
            stats.put("dataSource", SOURCE_MONGODB);
            return stats;
        }
    }

    /**
     * 获取用户消息统计
     */
    public java.util.Map<String, Object> getUserMessageStatistics(String userId) {
        if (isESEnabled()) {
            log.info("获取用户消息统计 -> ES: userId={}", userId);
            java.util.Map<String, Object> stats = esQueryService.getUserMessageStatistics(userId);
            stats.put("dataSource", SOURCE_ES);
            return stats;
        } else {
            log.info("获取用户消息统计 -> MongoDB: userId={}", userId);
            java.util.Map<String, Object> stats = mongoQueryService.getUserMessageStatistics(userId);
            stats.put("dataSource", SOURCE_MONGODB);
            return stats;
        }
    }

    /**
     * 获取会话消息统计
     */
    public java.util.Map<String, Object> getChatMessageStatistics(String chatId) {
        if (isESEnabled()) {
            log.info("获取会话消息统计 -> ES: chatId={}", chatId);
            java.util.Map<String, Object> stats = esQueryService.getChatMessageStatistics(chatId);
            stats.put("dataSource", SOURCE_ES);
            return stats;
        } else {
            log.info("获取会话消息统计 -> MongoDB: chatId={}", chatId);
            java.util.Map<String, Object> stats = mongoQueryService.getChatMessageStatistics(chatId);
            stats.put("dataSource", SOURCE_MONGODB);
            return stats;
        }
    }

    /**
     * 获取今日消息数（用于数据看板）
     */
    public Long getTodayMessageCount() {
        if (isESEnabled()) {
            log.info("获取今日消息数 -> ES");
            return esQueryService.getTodayMessageCount();
        } else {
            log.info("获取今日消息数 -> MongoDB");
            return mongoQueryService.getTodayMessageCount();
        }
    }

    /**
     * 获取消息趋势（用于数据看板）
     *
     * @param days 天数
     * @return Map<日期(MM-dd), 消息数>
     */
    public java.util.Map<String, Long> getMessagesTrend(int days) {
        if (isESEnabled()) {
            log.info("获取消息趋势 -> ES: days={}", days);
            return esQueryService.getMessagesTrend(days);
        } else {
            log.info("获取消息趋势 -> MongoDB: days={}", days);
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
