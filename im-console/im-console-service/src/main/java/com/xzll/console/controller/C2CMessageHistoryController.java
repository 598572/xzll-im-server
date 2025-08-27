package com.xzll.console.controller;

import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.service.ImC2CMsgRecordHBaseService;
import com.xzll.console.util.HBaseHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     */
    @GetMapping("/history/latest")
    public Map<String, Object> getLatestMessages(
            @RequestParam(defaultValue = "20") int limit) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (limit > 100) {
                limit = 100;
            }
            
            List<ImC2CMsgRecord> messages = imC2CMsgRecordHBaseService.getLatestMessages(limit);
            
            result.put("success", true);
            result.put("data", messages);
            result.put("count", messages.size());
            result.put("limit", limit);
            
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
     * 条件查询单聊历史消息
     */
    @GetMapping("/history/search")
    public Map<String, Object> searchMessageHistory(
            @RequestParam(required = false) String fromUserId,
            @RequestParam(required = false) String toUserId,
            @RequestParam(required = false) String chatId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<ImC2CMsgRecord> messages = imC2CMsgRecordHBaseService.getMessagesByCondition(fromUserId, toUserId, chatId);
            
            result.put("success", true);
            result.put("data", messages);
            result.put("count", messages.size());
            
        } catch (Exception e) {
            log.error("条件查询单聊历史消息失败", e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * HBase连接健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> checkHBaseHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean isHealthy = hBaseHealthChecker.isConnectionHealthy();
            String status = hBaseHealthChecker.getConnectionStatus();
            boolean tableExists = hBaseHealthChecker.isTableExists("im_c2c_msg_record");
            
            result.put("success", true);
            result.put("hbaseHealthy", isHealthy);
            result.put("connectionStatus", status);
            result.put("tableExists", tableExists);
            result.put("timestamp", System.currentTimeMillis());
            
            if (!isHealthy) {
                result.put("message", "HBase连接异常，请检查配置");
            } else {
                result.put("message", "HBase连接正常");
            }
            
        } catch (Exception e) {
            log.error("HBase健康检查失败", e);
            result.put("success", false);
            result.put("message", "健康检查失败: " + e.getMessage());
        }
        
        return result;
    }
} 