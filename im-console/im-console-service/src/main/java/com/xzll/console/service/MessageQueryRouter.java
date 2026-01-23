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
 * 路由策略:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  查询条件                        │  路由目标  │  原因            │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  仅 chatId（无其他条件）          │  HBase    │  RowKey前缀扫描   │
 * │  chatId + 时间范围                │  HBase    │  RowKey范围扫描   │
 * │  fromUserId / toUserId           │  ES       │  Keyword精确查询  │
 * │  消息内容搜索                     │  ES       │  全文搜索        │
 * │  复合条件组合                     │  ES       │  Bool复合查询    │
 * │  消息统计                         │  ES       │  聚合统计        │
 * │  最新消息列表                     │  ES       │  排序分页        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
public class MessageQueryRouter {

    @Resource
    private ImC2CMsgRecordHBaseService hBaseService;

    @Resource
    private MessageESQueryService esQueryService;

    /**
     * 数据来源标识
     */
    public static final String SOURCE_HBASE = "HBASE";
    public static final String SOURCE_ES = "ES";

    /**
     * 智能路由查询
     * 根据查询条件自动选择最优存储
     *
     * @param searchDTO 搜索条件
     * @return 搜索结果
     */
    public MessageSearchResultVO smartSearch(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();
        
        // 确定路由策略
        String routeTarget = determineRoute(searchDTO);
        log.info("查询路由决策: {} -> {}", summarizeConditions(searchDTO), routeTarget);

        MessageSearchResultVO result;
        
        if (SOURCE_HBASE.equals(routeTarget)) {
            // 走 HBase 查询
            result = searchFromHBase(searchDTO);
        } else {
            // 走 ES 查询
            result = esQueryService.search(searchDTO);
        }

        result.setDataSource(routeTarget);
        result.setCostMs(System.currentTimeMillis() - startTime);
        
        return result;
    }

    /**
     * 确定路由目标
     * 
     * 核心路由规则:
     * 1. 仅 chatId 条件 → HBase（RowKey前缀扫描最高效）
     * 2. 其他所有情况 → ES（复合查询能力强）
     */
    private String determineRoute(MessageSearchDTO searchDTO) {
        // 规则1: 仅有 chatId 条件，走 HBase
        if (searchDTO.isOnlyChatIdCondition()) {
            return SOURCE_HBASE;
        }
        
        // 规则2: 有 chatId + 时间范围，且无其他复杂条件，走 HBase
        // （HBase 支持 RowKey 范围扫描，时间是 RowKey 的一部分）
        if (StrUtil.isNotBlank(searchDTO.getChatId()) 
                && (searchDTO.getStartTime() != null || searchDTO.getEndTime() != null)
                && StrUtil.isBlank(searchDTO.getFromUserId())
                && StrUtil.isBlank(searchDTO.getToUserId())
                && StrUtil.isBlank(searchDTO.getContent())
                && searchDTO.getMsgStatus() == null
                && searchDTO.getMsgFormat() == null
                && searchDTO.getWithdrawFlag() == null) {
            // 注意: 当前 HBase 实现可能不支持时间范围，降级到 ES
            // 如果 HBase 有时间范围查询能力，可以返回 SOURCE_HBASE
            return SOURCE_ES;
        }
        
        // 规则3: 其他情况都走 ES
        return SOURCE_ES;
    }

    /**
     * 从 HBase 查询
     */
    private MessageSearchResultVO searchFromHBase(MessageSearchDTO searchDTO) {
        try {
            int pageSize = searchDTO.getPageSize() != null ? searchDTO.getPageSize() : 20;
            if (pageSize > 100) {
                pageSize = 100;
            }

            List<ImC2CMsgRecord> records;
            
            if (StrUtil.isNotBlank(searchDTO.getChatId())) {
                // 按 chatId 查询
                records = hBaseService.getMessagesByChatId(searchDTO.getChatId(), pageSize);
            } else {
                // 无条件查询（降级处理）
                records = hBaseService.getLatestMessages(pageSize);
            }

            // HBase 不支持精确总数统计，返回当前页数量
            long total = records.size();
            
            MessageSearchResultVO result = MessageSearchResultVO.success(
                    records, 
                    total, 
                    searchDTO.getPageNum() != null ? searchDTO.getPageNum() : 1, 
                    pageSize
            );
            result.setDataSource(SOURCE_HBASE);
            
            return result;

        } catch (Exception e) {
            log.error("HBase查询失败，降级到ES", e);
            // HBase 查询失败，降级到 ES
            return esQueryService.search(searchDTO);
        }
    }

    /**
     * 按 chatId 查询（路由版）
     * 优先走 HBase，因为这是 RowKey 前缀扫描的最佳场景
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
     * 始终走 ES，因为 HBase 不支持非 RowKey 字段查询
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByUserId(String userId, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();
        
        // 构建 OR 查询：发送方 OR 接收方
        MessageSearchDTO dtoFrom = new MessageSearchDTO();
        dtoFrom.setFromUserId(userId);
        dtoFrom.setPageNum(pageNum);
        dtoFrom.setPageSize(pageSize);
        
        // 使用 ES 的 should 查询
        MessageSearchResultVO result = esQueryService.search(dtoFrom);
        result.setDataSource(SOURCE_ES);
        result.setCostMs(System.currentTimeMillis() - startTime);
        
        log.info("按用户ID查询完成: userId={}, 命中={}, 耗时={}ms", userId, result.getTotal(), result.getCostMs());
        return result;
    }

    /**
     * 消息内容搜索
     * 始终走 ES，因为只有 ES 支持全文搜索
     *
     * @param content  搜索关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    public MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize) {
        long startTime = System.currentTimeMillis();
        
        MessageSearchResultVO result = esQueryService.searchByContent(content, pageNum, pageSize);
        result.setDataSource(SOURCE_ES);
        result.setCostMs(System.currentTimeMillis() - startTime);
        
        log.info("内容搜索完成: content={}, 命中={}, 耗时={}ms", content, result.getTotal(), result.getCostMs());
        return result;
    }

    /**
     * 获取最新消息
     * 走 ES，因为需要全局排序
     *
     * @param limit 数量限制
     * @return 消息列表
     */
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        return esQueryService.getLatestMessages(limit);
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
        return esQueryService.isConnectionHealthy();
    }
}
