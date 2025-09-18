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
            
            // 使用 Redisson 的原子增加操作
            RMap<String, Integer> map = redissonClient.getMap(redisKey);
            Integer newCount = map.merge(chatId, increment, Integer::sum);
            
            // 设置过期时间
            map.expire(EXPIRE_DAYS, TimeUnit.DAYS);
            
            log.debug("增加未读消息数成功: userId={}, chatId={}, increment={}, newCount={}", 
                    userId, chatId, increment, newCount);
        } catch (Exception e) {
            log.error("增加未读消息数失败: userId={}, chatId={}, increment={}", userId, chatId, increment, e);
            
            // 降级方案：使用读取-修改-写入
            try {
                fallbackIncrementUnreadCount(userId, chatId, increment);
            } catch (Exception fallbackEx) {
                log.error("降级增加未读消息数也失败: userId={}, chatId={}, increment={}", 
                    userId, chatId, increment, fallbackEx);
            }
        }
    }

    @Override
    public void incrementUnreadCount(String userId, String chatId) {
        incrementUnreadCount(userId, chatId, 1);
    }

    /**
     * 降级方案：使用读取-修改-写入的方式增加未读数
     */
    private void fallbackIncrementUnreadCount(String userId, String chatId, int increment) {
        String redisKey = buildRedisKey(userId);
        
        // 获取当前值
        String currentValue = redissonUtils.getHash(redisKey, chatId);
        int currentCount = 0;
        if (currentValue != null && !currentValue.isEmpty()) {
            try {
                currentCount = Integer.parseInt(currentValue);
            } catch (NumberFormatException e) {
                log.warn("解析当前未读数失败，重置为0: userId={}, chatId={}, value={}", userId, chatId, currentValue);
                currentCount = 0;
            }
        }
        
        // 计算新值
        int newCount = Math.max(0, currentCount + increment);
        
        // 写入新值
        redissonUtils.setHash(redisKey, chatId, String.valueOf(newCount));
        redissonUtils.expire(redisKey, EXPIRE_DAYS, TimeUnit.DAYS);
        
        log.info("降级方案增加未读消息数成功: userId={}, chatId={}, oldCount={}, increment={}, newCount={}", 
            userId, chatId, currentCount, increment, newCount);
    }

    @Override
    public void clearUnreadCount(String userId, String chatId) {
        try {
            String redisKey = buildRedisKey(userId);
            // 使用RedissonUtils方法
            redissonUtils.setHash(redisKey, chatId, "0");
            
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
            // 使用RedissonUtils方法
            redissonUtils.setHash(redisKey, chatId, String.valueOf(count));
            
            // 设置过期时间
            redissonUtils.expire(redisKey, EXPIRE_DAYS, TimeUnit.DAYS);
            
            log.debug("设置未读消息数成功: userId={}, chatId={}, count={}", userId, chatId, count);
        } catch (Exception e) {
            log.error("设置未读消息数失败: userId={}, chatId={}, count={}", userId, chatId, count, e);
        }
    }

    @Override
    public int getUnreadCount(String userId, String chatId) {
        try {
            String redisKey = buildRedisKey(userId);
            String countStr = redissonUtils.getHash(redisKey, chatId);
            
            if (countStr != null && !countStr.isEmpty()) {
                try {
                    int count = Integer.parseInt(countStr);
                    log.debug("获取未读消息数: userId={}, chatId={}, count={}", userId, chatId, count);
                    return Math.max(0, count); // 确保不返回负数
                } catch (NumberFormatException e) {
                    log.warn("解析未读数失败: userId={}, chatId={}, value={}", userId, chatId, countStr);
                    return 0;
                }
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
            Map<String, String> redisResult = redissonUtils.getAllHash(redisKey);
            
            for (Map.Entry<String, String> entry : redisResult.entrySet()) {
                String chatId = entry.getKey();
                String value = entry.getValue();
                
                // 解析字符串值为整数
                Integer count = 0;
                if (value != null && !value.isEmpty()) {
                    try {
                        count = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        log.warn("转换未读数失败，将设为0: userId={}, chatId={}, value={}", userId, chatId, value);
                        count = 0;
                    }
                }
                
                result.put(chatId, Math.max(0, count)); // 确保不返回负数
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
            redissonUtils.deleteHash(redisKey, chatId);
            
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

    /**
     * 清理指定用户的损坏未读数据
     * 当出现类型转换错误时可以调用此方法进行数据清理
     */
    public void cleanupCorruptedData(String userId) {
        try {
            String redisKey = buildRedisKey(userId);
            
            // 删除整个Hash，重新开始
            redissonUtils.delete(redisKey);
            
            log.info("清理用户损坏的未读数据成功: userId={}", userId);
        } catch (Exception e) {
            log.error("清理用户损坏的未读数据失败: userId={}", userId, e);
        }
    }

    /**
     * 修复指定用户指定会话的数据类型问题
     */
    public void fixDataType(String userId, String chatId) {
        try {
            String redisKey = buildRedisKey(userId);
            
            // 尝试获取原始值
            String rawValue = redissonUtils.getHash(redisKey, chatId);
            
            if (rawValue != null) {
                // 删除原始值
                redissonUtils.deleteHash(redisKey, chatId);
                
                // 设置为0重新开始
                redissonUtils.setHash(redisKey, chatId, "0");
                redissonUtils.expire(redisKey, EXPIRE_DAYS, TimeUnit.DAYS);
                
                log.info("修复数据类型成功: userId={}, chatId={}, oldValue={}", userId, chatId, rawValue);
            }
        } catch (Exception e) {
            log.error("修复数据类型失败: userId={}, chatId={}", userId, chatId, e);
        }
    }
}
