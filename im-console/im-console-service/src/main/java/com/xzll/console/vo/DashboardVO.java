package com.xzll.console.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 数据看板统计VO
 */
@Data
@Builder
public class DashboardVO {
    
    /**
     * 总用户数
     */
    private Long totalUsers;
    
    /**
     * 今日新增用户
     */
    private Long todayNewUsers;
    
    /**
     * 当前在线用户数
     */
    private Long onlineUsers;
    
    /**
     * 今日消息数
     */
    private Long todayMessages;
    
    /**
     * 总消息数
     */
    private Long totalMessages;
    
    /**
     * 总好友关系数
     */
    private Long totalFriendRelations;
    
    /**
     * 消息TPS（每秒消息数）
     */
    private Long messageTps;
    
    /**
     * 按终端类型统计用户
     * key: 终端类型名称, value: 用户数
     */
    private Map<String, Long> usersByTerminal;
    
    /**
     * 近7天消息趋势
     * key: 日期, value: 消息数
     */
    private Map<String, Long> messagesTrend;
    
    /**
     * 近7天用户注册趋势
     * key: 日期, value: 注册数
     */
    private Map<String, Long> usersTrend;
}
