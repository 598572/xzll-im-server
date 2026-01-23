package com.xzll.console.controller;

import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.service.ImC2CMsgRecordHBaseService;
import com.xzll.console.service.MessageESQueryService;
import com.xzll.console.service.MessageQueryRouter;
import com.xzll.console.util.HBaseHealthChecker;
import com.xzll.console.vo.MessageSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xzll.common.constant.ImConstant.TableConstant.IM_C2C_MSG_RECORD;

/**
 * @Author: hzz
 * @Date: 2025/8/27
 * @Description: 单聊历史消息查询Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/c2c/message")
@CrossOrigin
public class C2CMessageHistoryController {

    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;
    
    @Resource
    private HBaseHealthChecker hBaseHealthChecker;

    @Resource
    private MessageQueryRouter messageQueryRouter;

    @Resource
    private MessageESQueryService messageESQueryService;

    /**
     * 分页查询单聊历史消息
     */
    @GetMapping("/history/page")
    public Map<String, Object> getMessageHistoryByPage(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String lastRowKey) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (limit > 100) {
                limit = 100;
                log.warn("查询数量超过100，自动调整为100");
            }
            
            List<ImC2CMsgRecord> messages = imC2CMsgRecordHBaseService.getAllMessagesWithPagination(limit, lastRowKey);
            
            result.put("success", true);
            result.put("data", messages);
            result.put("count", messages.size());
            result.put("limit", limit);
            result.put("hasMore", messages.size() == limit);
            
            if (messages.size() == limit && !messages.isEmpty()) {
                ImC2CMsgRecord lastMessage = messages.get(messages.size() - 1);
                String nextRowKey = lastMessage.getChatId() + "_" + 
                    (Long.MAX_VALUE - lastMessage.getMsgCreateTime()) + "_" + 
                    lastMessage.getMsgId();
                result.put("nextRowKey", nextRowKey);
            }
            
            log.info("分页查询单聊历史消息成功，返回 {} 条记录", messages.size());
            
        } catch (Exception e) {
            log.error("分页查询单聊历史消息失败", e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取最新单聊消息
     * 数据来源: ES（全局排序效率高）
     */
    @GetMapping("/history/latest")
    public Map<String, Object> getLatestMessages(
            @RequestParam(defaultValue = "20") int limit) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (limit > 100) {
                limit = 100;
            }
            
            // 使用ES查询最新消息（全局排序效率更高）
            List<ImC2CMsgRecord> messages = messageQueryRouter.getLatestMessages(limit);
            
            result.put("success", true);
            result.put("data", messages);
            result.put("count", messages.size());
            result.put("limit", limit);
            result.put("dataSource", "ES");
            
        } catch (Exception e) {
            log.error("获取最新单聊消息失败", e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 根据会话ID查询单聊历史消息
     */
    @GetMapping("/history/chat/{chatId}")
    public Map<String, Object> getMessageHistoryByChatId(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "50") int limit) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (chatId == null || chatId.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "会话ID不能为空");
                return result;
            }
            
            if (limit > 100) {
                limit = 100;
            }
            
            List<ImC2CMsgRecord> messages = imC2CMsgRecordHBaseService.getMessagesByChatId(chatId, limit);
            
            result.put("success", true);
            result.put("data", messages);
            result.put("count", messages.size());
            result.put("chatId", chatId);
            result.put("limit", limit);
            
        } catch (Exception e) {
            log.error("根据会话ID查询单聊历史消息失败，chatId: {}", chatId, e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 条件查询单聊历史消息（智能路由版）
     * 根据查询条件自动选择最优存储：
     * - 仅chatId条件 → HBase（RowKey前缀扫描）
     * - 其他条件 → ES（复合查询）
     */
    @GetMapping("/history/search")
    public MessageSearchResultVO searchMessageHistory(
            @RequestParam(required = false) String fromUserId,
            @RequestParam(required = false) String toUserId,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Integer msgStatus,
            @RequestParam(required = false) Integer msgFormat,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            // 构建搜索条件
            MessageSearchDTO searchDTO = new MessageSearchDTO();
            searchDTO.setFromUserId(fromUserId);
            searchDTO.setToUserId(toUserId);
            searchDTO.setChatId(chatId);
            searchDTO.setContent(content);
            searchDTO.setMsgStatus(msgStatus);
            searchDTO.setMsgFormat(msgFormat);
            searchDTO.setStartTime(startTime);
            searchDTO.setEndTime(endTime);
            searchDTO.setPageNum(pageNum);
            searchDTO.setPageSize(pageSize);
            
            // 使用智能路由查询
            return messageQueryRouter.smartSearch(searchDTO);
            
        } catch (Exception e) {
            log.error("条件查询单聊历史消息失败", e);
            return MessageSearchResultVO.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 消息内容全文搜索
     * 数据来源: ES（支持中文分词）
     */
    @GetMapping("/history/search/content")
    public MessageSearchResultVO searchByContent(
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            return messageQueryRouter.searchByContent(content, pageNum, pageSize);
        } catch (Exception e) {
            log.error("消息内容搜索失败", e);
            return MessageSearchResultVO.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 按用户ID搜索消息
     * 数据来源: ES
     */
    @GetMapping("/history/search/user/{userId}")
    public MessageSearchResultVO searchByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            return messageQueryRouter.searchByUserId(userId, pageNum, pageSize);
        } catch (Exception e) {
            log.error("按用户ID搜索消息失败", e);
            return MessageSearchResultVO.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 按时间范围搜索消息
     * 数据来源: ES
     */
    @GetMapping("/history/search/time")
    public MessageSearchResultVO searchByTimeRange(
            @RequestParam Long startTime,
            @RequestParam Long endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            return messageESQueryService.searchByTimeRange(startTime, endTime, pageNum, pageSize);
        } catch (Exception e) {
            log.error("按时间范围搜索消息失败", e);
            return MessageSearchResultVO.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取消息统计信息
     * 数据来源: ES
     */
    @GetMapping("/statistics")
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> stats = messageESQueryService.getMessageStatistics();
            result.put("success", true);
            result.put("data", stats);
            result.put("dataSource", "ES");
        } catch (Exception e) {
            log.error("获取消息统计失败", e);
            result.put("success", false);
            result.put("message", "统计失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取用户消息统计
     * 数据来源: ES
     */
    @GetMapping("/statistics/user/{userId}")
    public Map<String, Object> getUserMessageStatistics(@PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> stats = messageESQueryService.getUserMessageStatistics(userId);
            result.put("success", true);
            result.put("data", stats);
            result.put("userId", userId);
            result.put("dataSource", "ES");
        } catch (Exception e) {
            log.error("获取用户消息统计失败", e);
            result.put("success", false);
            result.put("message", "统计失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取会话消息统计
     * 数据来源: ES
     */
    @GetMapping("/statistics/chat/{chatId}")
    public Map<String, Object> getChatMessageStatistics(@PathVariable String chatId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> stats = messageESQueryService.getChatMessageStatistics(chatId);
            result.put("success", true);
            result.put("data", stats);
            result.put("chatId", chatId);
            result.put("dataSource", "ES");
        } catch (Exception e) {
            log.error("获取会话消息统计失败", e);
            result.put("success", false);
            result.put("message", "统计失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 健康检查（HBase + ES）
     */
    @GetMapping("/health")
    public Map<String, Object> checkHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // HBase 健康检查
            boolean hbaseHealthy = hBaseHealthChecker.isConnectionHealthy();
            String hbaseStatus = hBaseHealthChecker.getConnectionStatus();
            boolean tableExists = hBaseHealthChecker.isTableExists(IM_C2C_MSG_RECORD);
            
            // ES 健康检查
            boolean esHealthy = messageESQueryService.isConnectionHealthy();
            Map<String, Object> esIndexInfo = messageESQueryService.getIndexInfo();
            
            result.put("success", true);
            result.put("timestamp", System.currentTimeMillis());
            
            // HBase 状态
            Map<String, Object> hbaseInfo = new HashMap<>();
            hbaseInfo.put("healthy", hbaseHealthy);
            hbaseInfo.put("status", hbaseStatus);
            hbaseInfo.put("tableExists", tableExists);
            result.put("hbase", hbaseInfo);
            
            // ES 状态
            Map<String, Object> esInfo = new HashMap<>();
            esInfo.put("healthy", esHealthy);
            esInfo.put("indexInfo", esIndexInfo);
            result.put("elasticsearch", esInfo);
            
            // 总体状态
            boolean allHealthy = hbaseHealthy && esHealthy;
            result.put("allHealthy", allHealthy);
            if (allHealthy) {
                result.put("message", "HBase和ES连接均正常");
            } else {
                result.put("message", "HBase: " + (hbaseHealthy ? "正常" : "异常") + 
                        ", ES: " + (esHealthy ? "正常" : "异常"));
            }
            
        } catch (Exception e) {
            log.error("健康检查失败", e);
            result.put("success", false);
            result.put("message", "健康检查失败: " + e.getMessage());
        }
        
        return result;
    }
} 