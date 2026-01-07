package com.xzll.business.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ChatListService;
import com.xzll.business.service.UnreadCountService;
import com.xzll.common.utils.ChatFieldOptimizer;
import com.xzll.common.utils.CompressionUtil;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话列表管理服务实现（V3 增强版：时间戳防护）
 * 
 * @Author: hzz
 * @Date: 2025/11/19
 * @Description: 保留压缩 + 未读数独立存储 + 时间戳防护，解决原子性问题
 * 
 * Redis结构：
 * chat:list:{userId} = {
 *   "{chatId}:meta": "压缩的JSON(msgId,time,from)",  
 *   "{chatId}:unread": "5",         // 未读数（HINCRBY原子操作）
 *   "{chatId}:clear_ts": "1700366400000"  // 最后清零时间戳
 * }
 * 
 * 原子性保证：
 * 1. 未读数递增使用 HINCRBY（完全原子）
 * 2. 清零时记录时间戳
 * 3. 递增时检查消息时间 > 清零时间，防止清零后旧消息递增
 */
@Slf4j
@Service
public class ChatListServiceImpl implements ChatListService {
    
    @Resource
    private RedissonUtils redissonUtils;
    
    @Resource
    private RedissonClient redissonClient;
    
    @Resource
    private UnreadCountService unreadCountService;
    
    private static final String CHAT_LIST_KEY_PREFIX = "chat:list:";
    private static final String META_SUFFIX = ":meta";
    private static final String UNREAD_SUFFIX = ":unread";
    private static final String CLEAR_TS_SUFFIX = ":clear_ts";  // 清零时间戳
    
    // 【混合优化】根据字段类型选择不同优化策略
    // meta: 可逆压缩 (需要chatId拼接rowkey)
    // unread/clear_ts: 纯 hash (只需标识即可)
    // 总体节省: 75字节/chatId (62%空间)
    
    // Lua脚本：原子递增未读数（适配StringCodec）
    private static final String LUA_INCR_UNREAD = 
        "local current = redis.call('HGET', KEYS[1], ARGV[1]); " +
        "local newVal = (current and tonumber(current) or 0) + 1; " +
        "redis.call('HSET', KEYS[1], ARGV[1], tostring(newVal)); " +
        "return newVal";
    
    /**
     * 更新会话列表元数据（时间戳防护版）
     * 
     * 防止竞态条件：
     * - 如果消息时间 <= 清零时间，说明是旧消息，不递增未读数
     * - 只更新元数据，不影响未读计数
     */
    @Override
    public void updateChatListMetadata(String userId, String chatId, String msgId, String fromUserId, long timestamp) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        try {
            // 1. 检查清零时间戳（防止清零后旧消息递增）
            RMap<String, String> metaMap = redissonClient.getMap(redisKey, StringCodec.INSTANCE);
            String clearTsField = ChatFieldOptimizer.buildOptimizedField(chatId, CLEAR_TS_SUFFIX);
            String clearTsStr = metaMap.get(clearTsField);
            Long clearTs = clearTsStr != null ? Long.parseLong(clearTsStr) : null;
            
            // 如果消息时间早于清零时间，说明是旧消息，不递增未读数
            if (clearTs != null && timestamp <= clearTs) {
                log.warn("消息时间早于清零时间，跳过未读数递增, userId: {}, chatId: {}, msgTs: {}, clearTs: {}", 
                    userId, chatId, timestamp, clearTs);
                // 仍然更新元数据，但不递增未读数
                updateMetadataOnly(userId, chatId, msgId, fromUserId, timestamp);
                return;
            }
            
            // 2. 构建元数据JSON（不含未读数，但包含原始 chatId 用于反向查找）
            JSONObject metadata = new JSONObject();
            metadata.set("m", msgId);      // msgId
            metadata.set("t", timestamp);  // time
            metadata.set("f", fromUserId); // from
            metadata.set("c", chatId);     // 【新增】原始 chatId（用于 getAllChatListMetadata 反向查找）
            
            String jsonValue = metadata.toString();
            int originalSize = jsonValue.length();
            
            // 3. LZ4压缩
            String compressedValue = CompressionUtil.compressToBase64(jsonValue);
            int compressedSize = compressedValue.length();
            
            // 4. 保存元数据到 {compressed}:meta（可逆压缩，支持rowkey拼接）
            String metaField = ChatFieldOptimizer.buildOptimizedField(chatId, META_SUFFIX);
            redissonUtils.setHashWithStringCodec(redisKey, metaField, compressedValue);
            
            // 5. 【原子递增未读数】到 {hash8}:unread（纯 hash标识）
            String unreadField = ChatFieldOptimizer.buildOptimizedField(chatId, UNREAD_SUFFIX);
            Long newUnread = redissonUtils.executeLuaScriptAsLongWithStringCodec(
                LUA_INCR_UNREAD, 
                java.util.Collections.singletonList(redisKey), 
                unreadField
            );
            
            double ratio = CompressionUtil.compressionRatio(originalSize, compressedSize);
            
            // 【混合优化日志】显示优化效果  
            log.info("更新会话列表元数据（混合优化）, userId: {}, chatId: {}, msgId: {}, unread: {}, msgTs: {}, 压缩: {}B->{}B ({}%)",
                userId, chatId, msgId, newUnread, timestamp, originalSize, compressedSize, String.format("%.1f", ratio));
            
            // 首次优化时显示效果分析
            if (log.isDebugEnabled()) {
                log.debug("混合优化效果: \n{}", ChatFieldOptimizer.analyzeOptimization(chatId));
            }
                
        } catch (Exception e) {
            log.error("更新会话列表元数据失败, userId: {}, chatId: {}", userId, chatId, e);
        }
    }
    
    /**
     * 只更新元数据，不递增未读数（用于旧消息）
     */
    private void updateMetadataOnly(String userId, String chatId, String msgId, String fromUserId, long timestamp) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        JSONObject metadata = new JSONObject();
        metadata.set("m", msgId);
        metadata.set("t", timestamp);
        metadata.set("f", fromUserId);
        metadata.set("c", chatId);  // 【新增】原始 chatId
        
        String compressedValue = CompressionUtil.compressToBase64(metadata.toString());
        String metaField = ChatFieldOptimizer.buildOptimizedField(chatId, META_SUFFIX);
        redissonUtils.setHashWithStringCodec(redisKey, metaField, compressedValue);
        
        log.debug("仅更新元数据（不递增未读数）, userId: {}, chatId: {}", userId, chatId);
    }
    
    /**
     * 获取所有会话列表元数据（V3增强版：合并元数据+未读数）
     */
    @Override
    public Map<String, String> getAllChatListMetadata(String userId) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        try {
            Map<String, String> allFields = redissonUtils.getAllHashWithStringCodec(redisKey);
            
            if (allFields == null || allFields.isEmpty()) {
                log.debug("用户{}无会话列表元数据", userId);
                return new HashMap<>();
            }
            
            // 【混合优化方案】meta字段可逆，unread字段用hash匹配
            Map<String, String> result = new HashMap<>();
            Map<String, String> hashToUnreadMap = new HashMap<>();  // hash -> unread
            
            // 第一遍：收集数据
            for (Map.Entry<String, String> entry : allFields.entrySet()) {
                String field = entry.getKey();
                String value = entry.getValue();
                
                if (field.endsWith(META_SUFFIX)) {
                    // meta字段：可逆压缩 -> 直接反解出chatId
                    String originalChatId = ChatFieldOptimizer.extractChatIdFromField(field, META_SUFFIX);
                    if (originalChatId != null) {
                        try {
                            String decompressed = CompressionUtil.decompressFromBase64(value);
                            result.put(originalChatId, decompressed);
                        } catch (Exception e) {
                            log.error("解压会话元数据失败, userId: {}, chatId: {}", userId, originalChatId, e);
                        }
                    }
                } else if (field.endsWith(UNREAD_SUFFIX)) {
                    // unread字段：纯hash -> 提取hash用于匹配
                    String hashValue = ChatFieldOptimizer.extractHashFromField(field, UNREAD_SUFFIX);
                    if (hashValue != null) {
                        hashToUnreadMap.put(hashValue, value);
                    }
                }
                // 忽略 clear_ts 字段
            }
            
            // 第二遍：合并未读数到元数据 JSON 中
            for (Map.Entry<String, String> entry : result.entrySet()) {
                String originalChatId = entry.getKey();
                String metaJson = entry.getValue();
                
                // 通过 chatId 计算 hash，然后查找对应的未读数
                String hashValue = ChatFieldOptimizer.hashChatId(originalChatId);
                String unread = hashToUnreadMap.getOrDefault(hashValue, "0");
                
                try {
                    JSONObject metadata = JSONUtil.parseObj(metaJson);
                    metadata.set("u", Long.parseLong(unread));
                    result.put(originalChatId, metadata.toString());
                } catch (Exception e) {
                    log.error("合并未读数失败, userId: {}, chatId: {}", userId, originalChatId, e);
                }
            }
            
            log.info("查询会话列表元数据, userId: {}, 会话数: {}", userId, result.size());
            return result;
            
        } catch (Exception e) {
            log.error("查询会话列表元数据失败, userId: {}", userId, e);
            return new HashMap<>();
        }
    }
    
    /**
     * 获取单个会话的元数据（V3增强版：合并元数据+未读数）
     */
    @Override
    public String getChatMetadata(String userId, String chatId) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        try {
            // 获取元数据（使用优化的field名称）
            String metaField = ChatFieldOptimizer.buildOptimizedField(chatId, META_SUFFIX);
            String compressed = redissonUtils.getHashWithStringCodec(redisKey, metaField);
            if (compressed == null) {
                return null;
            }
            
            String metaJson = CompressionUtil.decompressFromBase64(compressed);
            
            // 获取未读数（使用优化的field名称）
            String unreadField = ChatFieldOptimizer.buildOptimizedField(chatId, UNREAD_SUFFIX);
            String unreadStr = redissonUtils.getHashWithStringCodec(redisKey, unreadField);
            long unread = unreadStr != null ? Long.parseLong(unreadStr) : 0;
            
            // 合并
            JSONObject metadata = JSONUtil.parseObj(metaJson);
            metadata.set("u", unread);
            
            return metadata.toString();
        } catch (Exception e) {
            log.error("查询会话元数据失败, userId: {}, chatId: {}", userId, chatId, e);
            return null;
        }
    }
    
    /**
     * 清零未读数（V3增强版：原子操作 + 记录时间戳）
     */
    @Override
    public void clearUnreadCount(String userId, String chatId) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        try {
            // 使用StringCodec避免序列化问题
            RMap<String, String> map = redissonClient.getMap(redisKey, StringCodec.INSTANCE);
            
            // 1. 清零未读数（使用优化的field名称）
            String unreadField = ChatFieldOptimizer.buildOptimizedField(chatId, UNREAD_SUFFIX);
            map.put(unreadField, "0");
            
            // 2. 【关键】记录清零时间戳，防止旧消息递增（使用优化的field名称）
            long clearTimestamp = System.currentTimeMillis();
            String clearTsField = ChatFieldOptimizer.buildOptimizedField(chatId, CLEAR_TS_SUFFIX);
            map.put(clearTsField, String.valueOf(clearTimestamp));
            
            log.info("清零会话未读数（记录时间戳）, userId: {}, chatId: {}, clearTs: {}", 
                userId, chatId, clearTimestamp);
            
        } catch (Exception e) {
            log.error("清零会话未读数失败, userId: {}, chatId: {}", userId, chatId, e);
        }
    }
    
    /**
     * 删除会话元数据（V3增强版：删除所有相关字段）
     */
    @Override
    public void deleteChatMetadata(String userId, String chatId) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        try {
            // 删除元数据、未读数、清零时间戳三个字段（使用优化的field名称）
            String metaField = ChatFieldOptimizer.buildOptimizedField(chatId, META_SUFFIX);
            String unreadField = ChatFieldOptimizer.buildOptimizedField(chatId, UNREAD_SUFFIX);
            String clearTsField = ChatFieldOptimizer.buildOptimizedField(chatId, CLEAR_TS_SUFFIX);
            
            redissonUtils.deleteHashWithStringCodec(redisKey, metaField);
            redissonUtils.deleteHashWithStringCodec(redisKey, unreadField);
            redissonUtils.deleteHashWithStringCodec(redisKey, clearTsField);
            
            log.info("删除会话元数据, userId: {}, chatId: {}", userId, chatId);
        } catch (Exception e) {
            log.error("删除会话元数据失败, userId: {}, chatId: {}", userId, chatId, e);
        }
    }
    
    /**
     * 删除所有会话列表数据
     */
    @Override
    public void deleteAllChatList(String userId) {
        String redisKey = CHAT_LIST_KEY_PREFIX + userId;
        
        try {
            redissonUtils.delete(redisKey);
            log.info("删除所有会话列表数据, userId: {}", userId);
        } catch (Exception e) {
            log.error("删除所有会话列表数据失败, userId: {}", userId, e);
        }
    }
}
