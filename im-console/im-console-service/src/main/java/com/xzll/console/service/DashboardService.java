package com.xzll.console.service;

import com.xzll.console.vo.DashboardVO;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 数据看板服务接口
 */
public interface DashboardService {
    
    /**
     * 获取看板统计数据
     *
     * @return 看板数据
     */
    DashboardVO getDashboardStats();
    
    /**
     * 获取当前在线用户数
     *
     * @return 在线用户数
     */
    Long getOnlineUserCount();
    
    /**
     * 获取今日消息数
     *
     * @return 消息数
     */
    Long getTodayMessageCount();
    
    /**
     * 获取消息TPS
     *
     * @return TPS
     */
    Long getMessageTps();
}
