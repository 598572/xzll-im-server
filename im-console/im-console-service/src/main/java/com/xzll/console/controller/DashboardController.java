package com.xzll.console.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.service.DashboardService;
import com.xzll.console.vo.DashboardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 数据看板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {
    
    @Resource
    private DashboardService dashboardService;
    
    /**
     * 获取看板统计数据
     */
    @GetMapping("/stats")
    public WebBaseResponse<DashboardVO> getDashboardStats() {
        try {
            DashboardVO stats = dashboardService.getDashboardStats();
            return WebBaseResponse.returnResultSuccess(stats);
        } catch (Exception e) {
            log.error("获取看板统计数据失败", e);
            return WebBaseResponse.returnResultError("获取看板统计数据失败");
        }
    }
    
    /**
     * 获取当前在线用户数
     */
    @GetMapping("/online-count")
    public WebBaseResponse<Long> getOnlineUserCount() {
        try {
            Long count = dashboardService.getOnlineUserCount();
            return WebBaseResponse.returnResultSuccess(count);
        } catch (Exception e) {
            log.error("获取在线用户数失败", e);
            return WebBaseResponse.returnResultError("获取在线用户数失败");
        }
    }
    
    /**
     * 获取今日消息数
     */
    @GetMapping("/today-messages")
    public WebBaseResponse<Long> getTodayMessageCount() {
        try {
            Long count = dashboardService.getTodayMessageCount();
            return WebBaseResponse.returnResultSuccess(count);
        } catch (Exception e) {
            log.error("获取今日消息数失败", e);
            return WebBaseResponse.returnResultError("获取今日消息数失败");
        }
    }
    
    /**
     * 获取消息TPS
     */
    @GetMapping("/message-tps")
    public WebBaseResponse<Long> getMessageTps() {
        try {
            Long tps = dashboardService.getMessageTps();
            return WebBaseResponse.returnResultSuccess(tps);
        } catch (Exception e) {
            log.error("获取消息TPS失败", e);
            return WebBaseResponse.returnResultError("获取消息TPS失败");
        }
    }
}
