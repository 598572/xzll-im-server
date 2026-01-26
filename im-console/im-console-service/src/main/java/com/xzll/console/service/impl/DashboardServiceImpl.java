package com.xzll.console.service.impl;

import com.xzll.common.utils.RedissonUtils;
import com.xzll.console.entity.mongo.ImC2CMsgRecordMongo;
import com.xzll.console.mapper.ImFriendRelationMapper;
import com.xzll.console.mapper.ImUserMapper;
import com.xzll.console.service.DashboardService;
import com.xzll.console.service.MessageQueryRouter;
import com.xzll.console.vo.DashboardVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private MessageQueryRouter messageQueryRouter;
    
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
            // userLogin:status: 是一个Hash结构，存储所有在线用户的状态
            // Field: 用户ID, Value: 状态值
            // Hash的大小即为在线用户数
            String loginStatusKey = "userLogin:status:";
            long count = redissonUtils.sizeHashWithStringCodec(loginStatusKey);

            log.info("获取在线用户数成功: count={}", count);
            return count;
        } catch (Exception e) {
            log.error("获取在线用户数失败", e);
            return 0L;
        }
    }
    
    @Override
    public Long getTodayMessageCount() {
        try {
            // 优先从Redis获取
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String key = MESSAGE_COUNT_KEY + today;
            String count = redissonUtils.getString(key);
            if (count != null) {
                return Long.parseLong(count);
            }

            // Redis没有数据，使用路由器查询（根据配置选择ES或MongoDB）
            log.info("Redis中没有今日消息数据，使用路由器查询");
            return messageQueryRouter.getTodayMessageCount();
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
        try {
            // 从路由器获取消息统计（根据配置选择ES或MongoDB）
            Map<String, Object> stats = messageQueryRouter.getMessageStatistics();
            Long totalCount = (Long) stats.get("totalCount");
            log.info("获取总消息数: dataSource={}, totalCount={}", stats.get("dataSource"), totalCount);
            return totalCount != null ? totalCount : 0L;
        } catch (Exception e) {
            log.error("获取总消息数失败", e);
            return 0L;
        }
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

        // 优先从Redis获取统计数据（高性能）
        boolean hasRedisData = false;
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.format(formatter);
            String key = MESSAGE_COUNT_KEY + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            try {
                String count = redissonUtils.getString(key);
                if (count != null) {
                    result.put(dateStr, Long.parseLong(count));
                    hasRedisData = true;
                } else {
                    result.put(dateStr, 0L);
                }
            } catch (Exception e) {
                result.put(dateStr, 0L);
            }
        }

        // 如果Redis没有数据，使用路由器查询（根据配置选择ES或MongoDB）
        if (!hasRedisData) {
            log.info("Redis中没有消息统计数据，使用路由器查询");
            result = messageQueryRouter.getMessagesTrend(7);
        }

        return result;
    }

    /**
     * 从MongoDB获取近7天的消息趋势
     * @deprecated 已废弃，使用 messageQueryRouter.getMessagesTrend() 代替
     */
    @Deprecated
    private Map<String, Long> getMessagesTrendFromMongoDB() {
        Map<String, Long> result = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        try {
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);

                // 计算当天的时间范围（毫秒时间戳）
                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

                long startTime = startOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endTime = endOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                // 从MongoDB查询当天的消息数
                Query query = new Query(Criteria.where("msgCreateTime").gte(startTime).lt(endTime));
                long count = mongoTemplate.count(query, ImC2CMsgRecordMongo.class);

                result.put(dateStr, count);
                log.info("MongoDB查询消息趋势: {} = {}", dateStr, count);
            }
        } catch (Exception e) {
            log.error("从MongoDB获取消息趋势失败", e);
            // 失败时返回0
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);
                result.put(dateStr, 0L);
            }
        }

        return result;
    }
    
    private Map<String, Long> getUsersTrend() {
        Map<String, Long> result = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        try {
            // 从数据库获取近7天的注册数据
            List<Object[]> stats = imUserMapper.countByDateRange(7);

            // 转换为Map，key是日期字符串(MM-dd)，value是数量
            Map<String, Long> dbData = new HashMap<>();
            for (Object[] row : stats) {
                java.sql.Date sqlDate = (java.sql.Date) row[0];
                LocalDate date = sqlDate.toLocalDate();
                String dateStr = date.format(formatter);
                Long count = ((Number) row[1]).longValue();
                dbData.put(dateStr, count);
            }

            // 填充近7天的数据（没有数据的日期填0）
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);
                result.put(dateStr, dbData.getOrDefault(dateStr, 0L));
            }

            log.info("获取用户注册趋势: {}", result);
        } catch (Exception e) {
            log.error("获取用户注册趋势失败", e);
            // 失败时返回0
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);
                result.put(dateStr, 0L);
            }
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
