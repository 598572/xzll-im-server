package com.xzll.business.controller;

import com.xzll.business.service.UnreadCountService;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 未读消息数量测试控制器
 * 用于测试Redis未读消息数量管理功能
 * 
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 未读消息数量测试API
 */
@Slf4j
@RestController
@RequestMapping("/test/unread")
public class UnreadCountTestController {

    @Resource
    private UnreadCountService unreadCountService;

    /**
     * 增加未读数测试
     */
    @PostMapping("/increment")
    public WebBaseResponse<String> incrementUnreadCount(
            @RequestParam String userId,
            @RequestParam String chatId,
            @RequestParam(defaultValue = "1") int count) {
        
        try {
            unreadCountService.incrementUnreadCount(userId, chatId, count);
            log.info("增加未读数成功: userId={}, chatId={}, count={}", userId, chatId, count);
            return WebBaseResponse.returnResultSuccess("增加未读数成功");
        } catch (Exception e) {
            log.error("增加未读数失败: userId={}, chatId={}, count={}", userId, chatId, count, e);
            return WebBaseResponse.returnResultError("增加未读数失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个会话未读数测试
     */
    @GetMapping("/get")
    public WebBaseResponse<Integer> getUnreadCount(
            @RequestParam String userId,
            @RequestParam String chatId) {
        
        try {
            int count = unreadCountService.getUnreadCount(userId, chatId);
            log.info("获取未读数成功: userId={}, chatId={}, count={}", userId, chatId, count);
            return WebBaseResponse.returnResultSuccess(count);
        } catch (Exception e) {
            log.error("获取未读数失败: userId={}, chatId={}", userId, chatId, e);
            return WebBaseResponse.returnResultError("获取未读数失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户所有会话未读数测试
     */
    @GetMapping("/getAll")
    public WebBaseResponse<Map<String, Integer>> getAllUnreadCounts(@RequestParam String userId) {
        
        try {
            Map<String, Integer> counts = unreadCountService.getAllUnreadCounts(userId);
            log.info("获取所有未读数成功: userId={}, counts={}", userId, counts);
            return WebBaseResponse.returnResultSuccess(counts);
        } catch (Exception e) {
            log.error("获取所有未读数失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("获取所有未读数失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户总未读数测试
     */
    @GetMapping("/getTotal")
    public WebBaseResponse<Integer> getTotalUnreadCount(@RequestParam String userId) {
        
        try {
            int total = unreadCountService.getTotalUnreadCount(userId);
            log.info("获取总未读数成功: userId={}, total={}", userId, total);
            return WebBaseResponse.returnResultSuccess(total);
        } catch (Exception e) {
            log.error("获取总未读数失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("获取总未读数失败: " + e.getMessage());
        }
    }

    /**
     * 清零未读数测试
     */
    @PostMapping("/clear")
    public WebBaseResponse<String> clearUnreadCount(
            @RequestParam String userId,
            @RequestParam String chatId) {
        
        try {
            unreadCountService.clearUnreadCount(userId, chatId);
            log.info("清零未读数成功: userId={}, chatId={}", userId, chatId);
            return WebBaseResponse.returnResultSuccess("清零未读数成功");
        } catch (Exception e) {
            log.error("清零未读数失败: userId={}, chatId={}", userId, chatId, e);
            return WebBaseResponse.returnResultError("清零未读数失败: " + e.getMessage());
        }
    }

    /**
     * 设置未读数测试
     */
    @PostMapping("/set")
    public WebBaseResponse<String> setUnreadCount(
            @RequestParam String userId,
            @RequestParam String chatId,
            @RequestParam int count) {
        
        try {
            unreadCountService.setUnreadCount(userId, chatId, count);
            log.info("设置未读数成功: userId={}, chatId={}, count={}", userId, chatId, count);
            return WebBaseResponse.returnResultSuccess("设置未读数成功");
        } catch (Exception e) {
            log.error("设置未读数失败: userId={}, chatId={}, count={}", userId, chatId, count, e);
            return WebBaseResponse.returnResultError("设置未读数失败: " + e.getMessage());
        }
    }

    /**
     * 删除未读数记录测试
     */
    @DeleteMapping("/delete")
    public WebBaseResponse<String> deleteUnreadCount(
            @RequestParam String userId,
            @RequestParam String chatId) {
        
        try {
            unreadCountService.deleteUnreadCount(userId, chatId);
            log.info("删除未读数记录成功: userId={}, chatId={}", userId, chatId);
            return WebBaseResponse.returnResultSuccess("删除未读数记录成功");
        } catch (Exception e) {
            log.error("删除未读数记录失败: userId={}, chatId={}", userId, chatId, e);
            return WebBaseResponse.returnResultError("删除未读数记录失败: " + e.getMessage());
        }
    }
}
