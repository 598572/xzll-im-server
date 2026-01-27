package com.xzll.business.controller;

import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.service.MessageQueryRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息查询控制器（支持智能路由）
 *
 * 路由策略：
 * - 有chatId：走MongoDB（分片键，性能最优）
 * - 无chatId + ES开启：走ES（避免MongoDB跨分片查询）
 * - 无chatId + ES关闭：走MongoDB（兜底）
 */
@RestController
@RequestMapping("/api/c2c/msg")
@CrossOrigin
@Slf4j
public class ImC2CMsgRecordQueryController {

    private final MessageQueryRouter queryRouter;

    public ImC2CMsgRecordQueryController(MessageQueryRouter queryRouter) {
        this.queryRouter = queryRouter;
    }

    /**
     * 根据会话ID查询消息（有chatId，直接走MongoDB）
     */
    @GetMapping("/chat/{chatId}")
    public Page<ImC2CMsgRecordES> findByChatId(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据会话ID查询消息，chatId: {}, page: {}, size: {}", chatId, page, size);
        return queryRouter.findByChatId(chatId, page, size);
    }

    /**
     * 根据发送者ID查询消息（无chatId，智能路由）
     */
    @GetMapping("/from/{fromUserId}")
    public Page<ImC2CMsgRecordES> findByFromUserId(
            @PathVariable String fromUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据发送者ID查询消息，fromUserId: {}, page: {}, size: {}", fromUserId, page, size);
        return queryRouter.findByFromUserId(fromUserId, page, size);
    }

    /**
     * 根据接收者ID查询消息（无chatId，智能路由）
     */
    @GetMapping("/to/{toUserId}")
    public Page<ImC2CMsgRecordES> findByToUserId(
            @PathVariable String toUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据接收者ID查询消息，toUserId: {}, page: {}, size: {}", toUserId, page, size);
        return queryRouter.findByToUserId(toUserId, page, size);
    }

    /**
     * 根据消息内容搜索（无chatId，智能路由）
     */
    @GetMapping("/search/content")
    public Page<ImC2CMsgRecordES> searchByContent(
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据消息内容搜索，content: {}, page: {}, size: {}", content, page, size);
        return queryRouter.searchByContent(content, page, size);
    }

    /**
     * 根据会话ID和内容搜索（有chatId，直接走MongoDB）
     */
    @GetMapping("/search/chat-content")
    public Page<ImC2CMsgRecordES> searchByChatIdAndContent(
            @RequestParam String chatId,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据会话ID和内容搜索，chatId: {}, content: {}, page: {}, size: {}",
            chatId, content, page, size);
        return queryRouter.searchByChatIdAndContent(chatId, content, page, size);
    }

    /**
     * 根据时间范围查询（有chatId，直接走MongoDB）
     */
    @GetMapping("/search/time-range")
    public Page<ImC2CMsgRecordES> findByTimeRange(
            @RequestParam String chatId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据时间范围查询，chatId: {}, startTime: {}, endTime: {}, page: {}, size: {}",
            chatId, startTime, endTime, page, size);
        return queryRouter.findByTimeRange(chatId, startTime, endTime, page, size);
    }

    /**
     * 根据消息状态查询（有chatId，直接走MongoDB）
     */
    @GetMapping("/search/status")
    public Page<ImC2CMsgRecordES> findByMsgStatus(
            @RequestParam String chatId,
            @RequestParam Integer msgStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】根据消息状态查询，chatId: {}, msgStatus: {}, page: {}, size: {}",
            chatId, msgStatus, page, size);
        return queryRouter.findByMsgStatus(chatId, msgStatus, page, size);
    }

    /**
     * 复合查询（智能路由）
     */
    @PostMapping("/search/complex")
    public Page<ImC2CMsgRecordES> complexSearch(
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String fromUserId,
            @RequestParam(required = false) String toUserId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Integer msgStatus,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("【API调用】复合查询，chatId: {}, fromUserId: {}, toUserId: {}, content: {}, msgStatus: {}, startTime: {}, endTime: {}, page: {}, size: {}",
            chatId, fromUserId, toUserId, content, msgStatus, startTime, endTime, page, size);
        return queryRouter.complexSearch(chatId, fromUserId, toUserId, content, msgStatus, startTime, endTime, page, size);
    }

    /**
     * 获取当前数据源配置
     */
    @GetMapping("/datasource")
    public String getCurrentDataSource() {
        String dataSource = queryRouter.getCurrentDataSource();
        log.info("【API调用】获取当前数据源，dataSource: {}", dataSource);
        return "当前数据源: " + dataSource;
    }
}
