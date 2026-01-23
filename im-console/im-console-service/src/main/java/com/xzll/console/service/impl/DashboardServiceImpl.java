package com.xzll.console.service.impl;

import com.xzll.common.utils.RedissonUtils;
import com.xzll.console.mapper.ImFriendRelationMapper;
import com.xzll.console.mapper.ImUserMapper;
import com.xzll.console.service.DashboardService;
import com.xzll.console.vo.DashboardVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 数据看板服务实现
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {
    
    private static final String ONLINE_USERS_PATTERN = "im:user:online:*";
    private static final String MESSAGE_COUNT_KEY = "im:stats:message:count:";
    private static final String MESSAGE_TPS_KEY = "im:stats:message:tps";
    
    @Resource
    private ImUserMapper imUserMapper;
    
    @Resource
    private ImFriendRelationMapper friendRelationMapper;
    
    @Resource
    private RedissonUtils redissonUtils;
    
    @Override
    public DashboardVO getDashboardStats() {
        return DashboardVO.builder()
                .totalUsers(getTotalUsers())
                .todayNewUsers(getTodayNewUsers())
                .onlineUsers(getOnlineUserCount())
                .todayMessages(getTodayMessageCount())
                .totalMessages(getTotalMessageCount())
                .totalFriendRelations(getTotalFriendRelations())
                .messageTps(getMessageTps())
                .usersByTerminal(getUsersByTerminal())
                .messagesTrend(getMessagesTrend())
                .usersTrend(getUsersTrend())
                .build();
    }
    
    @Override
    public Long getOnlineUserCount() {
        try {
            // 使用HyperLogLog或计数器统计在线用户数
            // 这里查询数据库中记录的在线用户数量作为备选方案
            String countKey = "im:stats:online:count";
            String countStr = redissonUtils.getString(countKey);
            if (countStr != null) {
                return Long.parseLong(countStr);
            }
            // 如果没有统计计数，返回0
            return 0L;
        } catch (Exception e) {
            log.error("获取在线用户数失败", e);
            return 0L;
        }
    }
    
    @Override
    public Long getTodayMessageCount() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String key = MESSAGE_COUNT_KEY + today;
            String count = redissonUtils.getString(key);
            return count != null ? Long.parseLong(count) : 0L;
        } catch (Exception e) {
            log.error("获取今日消息数失败", e);
            return 0L;
        }
    }
    
    @Override
    public Long getMessageTps() {
        try {
            String tps = redissonUtils.getString(MESSAGE_TPS_KEY);
            return tps != null ? Long.parseLong(tps) : 0L;
        } catch (Exception e) {
            log.error("获取消息TPS失败", e);
            return 0L;
        }
    }
    
    private Long getTotalUsers() {
        return imUserMapper.countTotal();
    }
    
    private Long getTodayNewUsers() {
        return imUserMapper.countTodayRegistered();
    }
    
    private Long getTotalMessageCount() {
        // TODO: 从HBase或统计表获取总消息数
        return 0L;
    }
    
    private Long getTotalFriendRelations() {
        try {
            return friendRelationMapper.countTotalRelations();
        } catch (Exception e) {
            log.error("获取好友关系总数失败", e);
            return 0L;
        }
    }
    
    private Map<String, Long> getUsersByTerminal() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Android", 0L);
        result.put("iOS", 0L);
        result.put("小程序", 0L);
        result.put("Web", 0L);
        
        try {
            List<Object[]> stats = imUserMapper.countByTerminalType();
            for (Object[] row : stats) {
                Integer type = (Integer) row[0];
                Long count = ((Number) row[1]).longValue();
                String typeName = getTerminalTypeName(type);
                result.put(typeName, count);
            }
        } catch (Exception e) {
            log.error("按终端类型统计用户失败", e);
        }
        
        return result;
    }
    
    private Map<String, Long> getMessagesTrend() {
        Map<String, Long> result = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        // 获取近7天的消息趋势
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(formatter);
            String key = MESSAGE_COUNT_KEY + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            try {
                String count = redissonUtils.getString(key);
                result.put(dateStr, count != null ? Long.parseLong(count) : 0L);
            } catch (Exception e) {
                result.put(dateStr, 0L);
            }
        }
        
        return result;
    }
    
    private Map<String, Long> getUsersTrend() {
        Map<String, Long> result = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        // 获取近7天的用户注册趋势
        // TODO: 实现按日期统计注册用户
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(formatter);
            result.put(dateStr, 0L);
        }
        
        return result;
    }
    
    private String getTerminalTypeName(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "Android";
            case 2: return "iOS";
            case 3: return "小程序";
            case 4: return "Web";
            default: return "未知";
        }
    }
}
