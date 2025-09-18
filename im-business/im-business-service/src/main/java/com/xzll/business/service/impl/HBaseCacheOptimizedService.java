package com.xzll.business.service.impl;

import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * HBase查询缓存优化服务
 * 提供基于Redis的缓存优化方案
 * 
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 终极优化 - 缓存版本
 */
@Slf4j
@Service
public class HBaseCacheOptimizedService {

    @Resource
    private RedissonClient redissonClient;
    
    @Resource
    private RedissonUtils redissonUtils;

    /**
     * 缓存Key前缀
     */
    private static final String LAST_MSG_CACHE_PREFIX = "last_msg:";
    
    /**
     * 缓存过期时间（分钟）
     */
    private static final long CACHE_EXPIRE_MINUTES = 30;

    /**
     * 终极优化方案：缓存 + 智能批量查询
     * 
     * @param chatIds 会话ID列表
     * @param hbaseQueryFunction HBase查询函数（回调）
     * @return 查询结果
     */
    public Map<String, ImC2CMsgRecord> batchGetLastMessagesWithCache(
            List<String> chatIds, 
            HBaseQueryFunction hbaseQueryFunction) {
        
        if (chatIds == null || chatIds.isEmpty()) {
            return new HashMap<>();
        }
        
        log.info("缓存优化查询开始，会话数量: {}", chatIds.size());
        
        Map<String, ImC2CMsgRecord> result = new HashMap<>();
        List<String> cacheMissChatIds = new ArrayList<>();
        
        // 1. 批量检查缓存
        Map<String, ImC2CMsgRecord> cachedResults = batchGetFromCache(chatIds);
        result.putAll(cachedResults);
        
        // 2. 找出缓存未命中的chatId
        for (String chatId : chatIds) {
            if (!result.containsKey(chatId)) {
                cacheMissChatIds.add(chatId);
            }
        }
        
        log.info("缓存命中: {}, 缓存未命中: {}", cachedResults.size(), cacheMissChatIds.size());
        
        // 3. 查询缓存未命中的数据
        if (!cacheMissChatIds.isEmpty()) {
            Map<String, ImC2CMsgRecord> hbaseResults = hbaseQueryFunction.query(cacheMissChatIds);
            result.putAll(hbaseResults);
            
            // 4. 将查询结果写入缓存
            batchSetToCache(hbaseResults);
        }
        
        log.info("缓存优化查询完成，总计返回: {}条记录", result.size());
        return result;
    }

    /**
     * 批量从缓存获取
     */
    private Map<String, ImC2CMsgRecord> batchGetFromCache(List<String> chatIds) {
        Map<String, ImC2CMsgRecord> result = new HashMap<>();
        
        try {
            // 使用Redisson的批量操作
            for (String chatId : chatIds) {
                String cacheKey = LAST_MSG_CACHE_PREFIX + chatId;
                RMap<String, Object> map = redissonClient.getMap(cacheKey);
                
                if (map.isExists() && !map.isEmpty()) {
                    ImC2CMsgRecord record = convertMapToRecord(map.readAllMap());
                    if (record != null) {
                        result.put(chatId, record);
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量缓存查询失败", e);
        }
        
        return result;
    }

    /**
     * 批量写入缓存
     */
    private void batchSetToCache(Map<String, ImC2CMsgRecord> records) {
        try {
            for (Map.Entry<String, ImC2CMsgRecord> entry : records.entrySet()) {
                String chatId = entry.getKey();
                ImC2CMsgRecord record = entry.getValue();
                
                String cacheKey = LAST_MSG_CACHE_PREFIX + chatId;
                RMap<String, Object> map = redissonClient.getMap(cacheKey);
                
                // 将对象转换为Map存储
                Map<String, Object> recordMap = convertRecordToMap(record);
                map.putAll(recordMap);
                
                // 设置过期时间
                map.expire(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
            
            log.info("批量缓存写入完成，写入{}条记录", records.size());
        } catch (Exception e) {
            log.error("批量缓存写入失败", e);
        }
    }

    /**
     * 清除指定会话的缓存
     */
    public void evictCache(String chatId) {
        try {
            String cacheKey = LAST_MSG_CACHE_PREFIX + chatId;
            redissonClient.getMap(cacheKey).delete();
            log.debug("清除缓存成功: chatId={}", chatId);
        } catch (Exception e) {
            log.error("清除缓存失败: chatId={}", chatId, e);
        }
    }

    /**
     * 批量清除缓存
     */
    public void evictBatchCache(List<String> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) {
            return;
        }
        
        try {
            for (String chatId : chatIds) {
                String cacheKey = LAST_MSG_CACHE_PREFIX + chatId;
                redissonClient.getMap(cacheKey).deleteAsync();
            }
            log.info("批量清除缓存完成，清除{}个会话缓存", chatIds.size());
        } catch (Exception e) {
            log.error("批量清除缓存失败", e);
        }
    }

    /**
     * 预热缓存
     */
    public void warmupCache(List<String> chatIds, HBaseQueryFunction hbaseQueryFunction) {
        log.info("开始预热缓存，会话数量: {}", chatIds.size());
        
        try {
            Map<String, ImC2CMsgRecord> results = hbaseQueryFunction.query(chatIds);
            batchSetToCache(results);
            log.info("缓存预热完成");
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats(List<String> chatIds) {
        int total = chatIds.size();
        int cached = 0;
        
        for (String chatId : chatIds) {
            String cacheKey = LAST_MSG_CACHE_PREFIX + chatId;
            if (redissonClient.getMap(cacheKey).isExists()) {
                cached++;
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("cached", cached);
        stats.put("hitRate", total > 0 ? (double) cached / total : 0.0);
        
        return stats;
    }

    /**
     * 将Record转换为Map
     */
    private Map<String, Object> convertRecordToMap(ImC2CMsgRecord record) {
        Map<String, Object> map = new HashMap<>();
        if (record != null) {
            map.put("msgId", record.getMsgId());
            map.put("chatId", record.getChatId());
            map.put("fromUserId", record.getFromUserId());
            map.put("toUserId", record.getToUserId());
            map.put("msgContent", record.getMsgContent());
            map.put("msgFormat", record.getMsgFormat());
            map.put("msgCreateTime", record.getMsgCreateTime());
            map.put("msgStatus", record.getMsgStatus());
            // 添加其他需要的字段...
        }
        return map;
    }

    /**
     * 将Map转换为Record
     */
    private ImC2CMsgRecord convertMapToRecord(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        
        ImC2CMsgRecord record = new ImC2CMsgRecord();
        record.setMsgId((String) map.get("msgId"));
        record.setChatId((String) map.get("chatId"));
        record.setFromUserId((String) map.get("fromUserId"));
        record.setToUserId((String) map.get("toUserId"));
        record.setMsgContent((String) map.get("msgContent"));
        record.setMsgFormat((Integer) map.get("msgFormat"));
        record.setMsgCreateTime((Long) map.get("msgCreateTime"));
        record.setMsgStatus((Integer) map.get("msgStatus"));
        // 设置其他字段...
        
        return record;
    }

    /**
     * HBase查询函数接口
     */
    @FunctionalInterface
    public interface HBaseQueryFunction {
        Map<String, ImC2CMsgRecord> query(List<String> chatIds);
    }
}
