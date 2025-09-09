package com.xzll.business.service.impl;

import com.xzll.business.service.UnreadCountService;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 未读消息数量管理服务实现
 * 使用Redisson Hash结构存储: unread:{userId} -> {chatId: count}
 * 
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: Redisson未读消息数量管理实现
 */
@Slf4j
@Service
public class UnreadCountServiceImpl implements UnreadCountService {

    @Resource
    private RedissonClient redissonClient;
    
    @Resource
    private RedissonUtils redissonUtils;

    /**
     * Redis Key前缀
     */
    private static final String UNREAD_COUNT_KEY_PREFIX = "unread:";
    
    /**
     * Redis Key过期时间（天）
     */
    private static final long EXPIRE_DAYS = 30;

    /**
     * 构建Redis Key
     */
    private String buildRedisKey(String userId) {
        return UNREAD_COUNT_KEY_PREFIX + userId;
    }

    @Override
    public void incrementUnreadCount(String userId, String chatId, int increment) {
        if (increment <= 0) {
            log.warn("增加未读数的数量必须大于0: userId={}, chatId={}, increment={}", userId, chatId, increment);
            return;
        }

        try {
            String redisKey = buildRedisKey(userId);
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            
            // 使用addAndGet进行原子性自增操作
            Integer newCount = map.addAndGet(chatId, increment);
            
            // 设置过期时间
            map.expire(EXPIRE_DAYS, TimeUnit.DAYS);
            
            log.debug("增加未读消息数成功: userId={}, chatId={}, increment={}, newCount={}", 
                    userId, chatId, increment, newCount);
        } catch (Exception e) {
            log.error("增加未读消息数失败: userId={}, chatId={}, increment={}", userId, chatId, increment, e);
        }
    }

    @Override
    public void incrementUnreadCount(String userId, String chatId) {
        incrementUnreadCount(userId, chatId, 1);
    }

    @Override
    public void clearUnreadCount(String userId, String chatId) {
        try {
            String redisKey = buildRedisKey(userId);
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            map.put(chatId, 0);
            
            log.debug("清零未读消息数成功: userId={}, chatId={}", userId, chatId);
        } catch (Exception e) {
            log.error("清零未读消息数失败: userId={}, chatId={}", userId, chatId, e);
        }
    }

    @Override
    public void setUnreadCount(String userId, String chatId, int count) {
        if (count < 0) {
            log.warn("未读消息数不能为负数: userId={}, chatId={}, count={}", userId, chatId, count);
            return;
        }

        try {
            String redisKey = buildRedisKey(userId);
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            map.put(chatId, count);
            
            // 设置过期时间
            map.expire(EXPIRE_DAYS, TimeUnit.DAYS);
            
            log.debug("设置未读消息数成功: userId={}, chatId={}, count={}", userId, chatId, count);
        } catch (Exception e) {
            log.error("设置未读消息数失败: userId={}, chatId={}, count={}", userId, chatId, count, e);
        }
    }

    @Override
    public int getUnreadCount(String userId, String chatId) {
        try {
            String redisKey = buildRedisKey(userId);
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            Integer count = map.get(chatId);
            
            if (count != null) {
                log.debug("获取未读消息数: userId={}, chatId={}, count={}", userId, chatId, count);
                return Math.max(0, count); // 确保不返回负数
            }
            
            log.debug("未找到未读消息数记录: userId={}, chatId={}", userId, chatId);
            return 0;
        } catch (Exception e) {
            log.error("获取未读消息数失败: userId={}, chatId={}", userId, chatId, e);
            return 0;
        }
    }

    @Override
    public Map<String, Integer> getAllUnreadCounts(String userId) {
        Map<String, Integer> result = new HashMap<>();
        
        try {
            String redisKey = buildRedisKey(userId);
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            Map<String, Integer> redisResult = map.readAllMap();
            
            for (Map.Entry<String, Integer> entry : redisResult.entrySet()) {
                String chatId = entry.getKey();
                Integer count = entry.getValue();
                result.put(chatId, Math.max(0, count != null ? count : 0)); // 确保不返回负数
            }
            
            log.debug("批量获取未读消息数成功: userId={}, chatCount={}", userId, result.size());
        } catch (Exception e) {
            log.error("批量获取未读消息数失败: userId={}", userId, e);
        }
        
        return result;
    }

    @Override
    public void deleteUnreadCount(String userId, String chatId) {
        try {
            String redisKey = buildRedisKey(userId);
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            map.remove(chatId);
            
            log.debug("删除未读消息数记录成功: userId={}, chatId={}", userId, chatId);
        } catch (Exception e) {
            log.error("删除未读消息数记录失败: userId={}, chatId={}", userId, chatId, e);
        }
    }

    @Override
    public int getTotalUnreadCount(String userId) {
        try {
            Map<String, Integer> allCounts = getAllUnreadCounts(userId);
            int total = allCounts.values().stream().mapToInt(Integer::intValue).sum();
            
            log.debug("获取用户总未读数: userId={}, totalCount={}", userId, total);
            return total;
        } catch (Exception e) {
            log.error("获取用户总未读数失败: userId={}", userId, e);
            return 0;
        }
    }
}
