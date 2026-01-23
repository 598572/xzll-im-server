package com.xzll.console.service;

import cn.hutool.core.util.StrUtil;
import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.vo.MessageSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 消息查询路由服务
 * 根据查询条件智能选择最优存储进行查询
 * 
 * 路由策略 (MongoDB 版本):
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  查询条件                        │  路由目标   │  原因            │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  所有查询条件                    │  MongoDB   │  索引匹配查询     │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  chatId + 条件组合               │  MongoDB   │  复合索引查询     │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  消息内容搜索                     │  MongoDB   │  正则模糊匹配     │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 注意：已将查询存储从 ES 迁移到 MongoDB，ES 仅作为异步同步目标
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
public class MessageQueryRouter {

    @Resource
    private MessageMongoQueryService mongoQueryService;

    /**
     * 数据来源标识
     */
    public static final String SOURCE_MONGODB = "MongoDB";

    /**
     * 智能路由查询
     * 现在统一使用 MongoDB 查询
     *
     * @param searchDTO 搜索条件
     * @return 搜索结果
     */
    public MessageSearchResultVO smartSearch(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();
        
        log.info("查询路由决策: {} -> MongoDB", summarizeConditions(searchDTO));

        // 统一使用 MongoDB 查询
        MessageSearchResultVO result = mongoQueryService.search(searchDTO);
        result.setDataSource(SOURCE_MONGODB);
        result.setCostMs(System.currentTimeMillis() - startTime);
        
        return result;
    }

    /**
     * 按 chatId 查询（使用 MongoDB）
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
     * 使用 MongoDB 查询
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByUserId(String userId, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();
        
        // 构建查询条件：发送方
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setFromUserId(userId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        
        MessageSearchResultVO result = mongoQueryService.search(dto);
        result.setDataSource(SOURCE_MONGODB);
        result.setCostMs(System.currentTimeMillis() - startTime);
        
        log.info("按用户ID查询完成: userId={}, 命中={}, 耗时={}ms", userId, result.getTotal(), result.getCostMs());
        return result;
    }

    /**
     * 消息内容搜索
     * 使用 MongoDB 正则匹配
     *
     * @param content  搜索关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();
        
        MessageSearchResultVO result = mongoQueryService.searchByContent(content, pageNum, pageSize);
        result.setDataSource(SOURCE_MONGODB);
        result.setCostMs(System.currentTimeMillis() - startTime);
        
        log.info("内容搜索完成: content={}, 命中={}, 耗时={}ms", content, result.getTotal(), result.getCostMs());
        return result;
    }

    /**
     * 获取最新消息
     * 使用 MongoDB 查询
     *
     * @param limit 数量限制
     * @return 消息列表
     */
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        return mongoQueryService.getLatestMessages(limit);
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

    /**
     * 获取健康状态
     */
    public boolean isHealthy() {
        return mongoQueryService.isConnectionHealthy();
    }
}
