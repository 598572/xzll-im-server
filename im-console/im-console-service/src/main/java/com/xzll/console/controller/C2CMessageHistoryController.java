package com.xzll.console.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.service.MessageMongoQueryService;
import com.xzll.console.service.MessageQueryRouter;
import com.xzll.console.vo.MessagePageResultVO;
import com.xzll.console.vo.MessageSearchResultVO;
import com.xzll.console.vo.HealthCheckVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
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
    private MessageQueryRouter messageQueryRouter;

    /**
     * 分页查询单聊历史消息
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/page")
    public WebBaseResponse<MessagePageResultVO> getMessageHistoryByPage(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String lastRowKey) {

        try {
            if (limit > 100) {
                limit = 100;
                log.warn("查询数量超过100，自动调整为100");
            }

            // 使用路由器查询最新消息（自动选择数据源）
            List<ImC2CMsgRecord> messages = messageQueryRouter.getLatestMessages(limit);

            MessagePageResultVO resultVO = new MessagePageResultVO();
            resultVO.setData(messages);
            resultVO.setCount(messages.size());
            resultVO.setLimit(limit);
            resultVO.setHasMore(messages.size() == limit);
            resultVO.setDataSource(messageQueryRouter.getCurrentDataSource());

            log.info("分页查询单聊历史消息成功，返回 {} 条记录，数据源: {}", messages.size(), resultVO.getDataSource());
            return WebBaseResponse.returnResultSuccess(resultVO);

        } catch (Exception e) {
            log.error("分页查询单聊历史消息失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新单聊消息
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/latest")
    public WebBaseResponse<MessagePageResultVO> getLatestMessages(
            @RequestParam(defaultValue = "20") int limit) {

        try {
            if (limit > 100) {
                limit = 100;
            }

            // 使用路由器查询最新消息（自动选择数据源）
            List<ImC2CMsgRecord> messages = messageQueryRouter.getLatestMessages(limit);

            MessagePageResultVO resultVO = new MessagePageResultVO();
            resultVO.setData(messages);
            resultVO.setCount(messages.size());
            resultVO.setLimit(limit);
            resultVO.setDataSource(messageQueryRouter.getCurrentDataSource());

            return WebBaseResponse.returnResultSuccess(resultVO);

        } catch (Exception e) {
            log.error("获取最新单聊消息失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据会话ID查询单聊历史消息
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/chat/{chatId}")
    public WebBaseResponse<MessagePageResultVO> getMessageHistoryByChatId(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "50") int limit) {

        try {
            if (chatId == null || chatId.trim().isEmpty()) {
                return WebBaseResponse.returnResultError("会话ID不能为空");
            }

            if (limit > 100) {
                limit = 100;
            }

            // 使用路由器查询（自动选择数据源）
            MessageSearchResultVO searchResult = messageQueryRouter.searchByChatId(chatId, 1, limit);
            List<ImC2CMsgRecord> messages = searchResult.getData();

            MessagePageResultVO resultVO = new MessagePageResultVO();
            resultVO.setData(messages);
            resultVO.setCount(messages.size());
            resultVO.setChatId(chatId);
            resultVO.setLimit(limit);
            resultVO.setDataSource(messageQueryRouter.getCurrentDataSource());

            return WebBaseResponse.returnResultSuccess(resultVO);

        } catch (Exception e) {
            log.error("根据会话ID查询单聊历史消息失败，chatId: {}", chatId, e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 条件查询单聊历史消息（智能路由版）
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/search")
    public WebBaseResponse<MessageSearchResultVO> searchMessageHistory(
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
            MessageSearchResultVO resultVO = messageQueryRouter.smartSearch(searchDTO);
            return WebBaseResponse.returnResultSuccess(resultVO);
            
        } catch (Exception e) {
            log.error("条件查询单聊历史消息失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 消息内容全文搜索
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/search/content")
    public WebBaseResponse<MessageSearchResultVO> searchByContent(
            @RequestParam String content,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        try {
            MessageSearchResultVO resultVO = messageQueryRouter.searchByContent(content, pageNum, pageSize);
            return WebBaseResponse.returnResultSuccess(resultVO);
        } catch (Exception e) {
            log.error("消息内容搜索失败", e);
            return WebBaseResponse.returnResultError("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 按用户ID搜索消息
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/search/user/{userId}")
    public WebBaseResponse<MessageSearchResultVO> searchByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        try {
            MessageSearchResultVO resultVO = messageQueryRouter.searchByUserId(userId, pageNum, pageSize);
            return WebBaseResponse.returnResultSuccess(resultVO);
        } catch (Exception e) {
            log.error("按用户ID搜索消息失败", e);
            return WebBaseResponse.returnResultError("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 按时间范围搜索消息
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/history/search/time")
    public WebBaseResponse<MessageSearchResultVO> searchByTimeRange(
            @RequestParam Long startTime,
            @RequestParam Long endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        try {
            // 构建搜索条件
            MessageSearchDTO searchDTO = new MessageSearchDTO();
            searchDTO.setStartTime(startTime);
            searchDTO.setEndTime(endTime);
            searchDTO.setPageNum(pageNum);
            searchDTO.setPageSize(pageSize);

            // 使用路由器查询（自动选择数据源）
            MessageSearchResultVO resultVO = messageQueryRouter.smartSearch(searchDTO);
            return WebBaseResponse.returnResultSuccess(resultVO);
        } catch (Exception e) {
            log.error("按时间范围搜索消息失败", e);
            return WebBaseResponse.returnResultError("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取消息统计信息
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/statistics")
    public WebBaseResponse<Map<String, Object>> getMessageStatistics() {
        try {
            Map<String, Object> stats = messageQueryRouter.getMessageStatistics();
            return WebBaseResponse.returnResultSuccess(stats);
        } catch (Exception e) {
            log.error("获取消息统计失败", e);
            return WebBaseResponse.returnResultError("统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户消息统计
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/statistics/user/{userId}")
    public WebBaseResponse<Map<String, Object>> getUserMessageStatistics(@PathVariable String userId) {
        try {
            Map<String, Object> stats = messageQueryRouter.getUserMessageStatistics(userId);
            return WebBaseResponse.returnResultSuccess(stats);
        } catch (Exception e) {
            log.error("获取用户消息统计失败", e);
            return WebBaseResponse.returnResultError("统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话消息统计
     * 数据来源: 根据 im.elasticsearch.syncEnabled 配置自动选择（ES/MongoDB）
     */
    @GetMapping("/statistics/chat/{chatId}")
    public WebBaseResponse<Map<String, Object>> getChatMessageStatistics(@PathVariable String chatId) {
        try {
            Map<String, Object> stats = messageQueryRouter.getChatMessageStatistics(chatId);
            return WebBaseResponse.returnResultSuccess(stats);
        } catch (Exception e) {
            log.error("获取会话消息统计失败", e);
            return WebBaseResponse.returnResultError("统计失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     * 根据 im.elasticsearch.syncEnabled 配置检查对应数据源
     */
    @GetMapping("/health")
    public WebBaseResponse<HealthCheckVO> checkHealth() {
        try {
            HealthCheckVO vo = new HealthCheckVO();
            vo.setTimestamp(System.currentTimeMillis());

            // 根据配置检查对应的数据源
            boolean useES = messageQueryRouter.getCurrentDataSource().equals(MessageQueryRouter.SOURCE_ES);
            boolean healthy = messageQueryRouter.isHealthy();

            // MongoDB 状态（复用hbase字段）
            HealthCheckVO.StorageHealth mongoHealth = new HealthCheckVO.StorageHealth();
            if (!useES) {
                mongoHealth.setHealthy(healthy);
                mongoHealth.setStatus(healthy ? "MongoDB连接正常" : "MongoDB连接异常");
                mongoHealth.setTableExists(true);
            } else {
                mongoHealth.setHealthy(true);
                mongoHealth.setStatus("MongoDB未启用");
                mongoHealth.setTableExists(false);
            }
            vo.setHbase(mongoHealth);

            // ES 状态
            HealthCheckVO.StorageHealth esHealth = new HealthCheckVO.StorageHealth();
            if (useES) {
                esHealth.setHealthy(healthy);
                esHealth.setStatus(healthy ? "ES连接正常" : "ES连接异常");
            } else {
                esHealth.setHealthy(true);
                esHealth.setStatus("ES未启用");
            }
            vo.setElasticsearch(esHealth);

            // 总体状态
            vo.setAllHealthy(healthy);

            String dataSource = messageQueryRouter.getCurrentDataSource();
            String msg = healthy ?
                    String.format("%s连接正常", dataSource) :
                    String.format("%s连接异常", dataSource);

            return WebBaseResponse.returnResultSuccess(msg, vo);

        } catch (Exception e) {
            log.error("健康检查失败", e);
            return WebBaseResponse.returnResultError("健康检查失败: " + e.getMessage());
        }
    }
} 