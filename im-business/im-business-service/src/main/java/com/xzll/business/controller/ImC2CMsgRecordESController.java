package com.xzll.business.controller;

import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.service.ImC2CMsgRecordESQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息ES查询控制器
 */
@RestController
@RequestMapping("/es/c2c/msg")
@CrossOrigin
@Slf4j
public class ImC2CMsgRecordESController {

    private final ImC2CMsgRecordESQueryService esQueryService;

    public ImC2CMsgRecordESController(ImC2CMsgRecordESQueryService esQueryService) {
        this.esQueryService = esQueryService;
    }

    /**
     * 根据会话ID查询消息
     */
    @GetMapping("/chat/{chatId}")
    public Page<ImC2CMsgRecordES> findByChatId(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据会话ID查询消息，chatId: {}, page: {}, size: {}", chatId, page, size);
        return esQueryService.findByChatId(chatId, pageable);
    }

    /**
     * 根据发送者ID查询消息
     */
    @GetMapping("/from/{fromUserId}")
    public Page<ImC2CMsgRecordES> findByFromUserId(
            @PathVariable String fromUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据发送者ID查询消息，fromUserId: {}, page: {}, size: {}", fromUserId, page, size);
        return esQueryService.findByFromUserId(fromUserId, pageable);
    }

    /**
     * 根据接收者ID查询消息
     */
    @GetMapping("/to/{toUserId}")
    public Page<ImC2CMsgRecordES> findByToUserId(
            @PathVariable String toUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据接收者ID查询消息，toUserId: {}, page: {}, size: {}", toUserId, page, size);
        return esQueryService.findByToUserId(toUserId, pageable);
    }

    /**
     * 根据消息内容搜索
     */
    @GetMapping("/search/content")
    public Page<ImC2CMsgRecordES> searchByContent(
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据消息内容搜索，content: {}, page: {}, size: {}", content, page, size);
        return esQueryService.searchByContent(content, pageable);
    }

    /**
     * 根据会话ID和内容搜索
     */
    @GetMapping("/search/chat-content")
    public Page<ImC2CMsgRecordES> searchByChatIdAndContent(
            @RequestParam String chatId,
            @RequestParam String content,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据会话ID和内容搜索，chatId: {}, content: {}, page: {}, size: {}", chatId, content, page, size);
        return esQueryService.searchByChatIdAndContent(chatId, content, pageable);
    }

    /**
     * 根据时间范围查询
     */
    @GetMapping("/search/time-range")
    public Page<ImC2CMsgRecordES> findByTimeRange(
            @RequestParam String chatId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据时间范围查询，chatId: {}, startTime: {}, endTime: {}, page: {}, size: {}", 
                chatId, startTime, endTime, page, size);
        return esQueryService.findByTimeRange(chatId, startTime, endTime, pageable);
    }

    /**
     * 根据消息状态查询
     */
    @GetMapping("/search/status")
    public Page<ImC2CMsgRecordES> findByMsgStatus(
            @RequestParam String chatId,
            @RequestParam Integer msgStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("根据消息状态查询，chatId: {}, msgStatus: {}, page: {}, size: {}", chatId, msgStatus, page, size);
        return esQueryService.findByMsgStatus(chatId, msgStatus, pageable);
    }

    /**
     * 复合查询
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
        
        Pageable pageable = PageRequest.of(page, size);
        log.info("复合查询，chatId: {}, fromUserId: {}, toUserId: {}, content: {}, msgStatus: {}, startTime: {}, endTime: {}, page: {}, size: {}", 
                chatId, fromUserId, toUserId, content, msgStatus, startTime, endTime, page, size);
        return esQueryService.complexSearch(chatId, fromUserId, toUserId, content, msgStatus, startTime, endTime, pageable);
    }
} 