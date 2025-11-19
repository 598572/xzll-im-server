package com.xzll.business.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ChatListService;
import com.xzll.business.service.UnreadCountService;
import com.xzll.common.utils.CompressionUtil;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
            String clearTsStr = metaMap.get(chatId + CLEAR_TS_SUFFIX);
            Long clearTs = clearTsStr != null ? Long.parseLong(clearTsStr) : null;
            
            // 如果消息时间早于清零时间，说明是旧消息，不递增未读数
            if (clearTs != null && timestamp <= clearTs) {
                log.warn("消息时间早于清零时间，跳过未读数递增, userId: {}, chatId: {}, msgTs: {}, clearTs: {}", 
                    userId, chatId, timestamp, clearTs);
                // 仍然更新元数据，但不递增未读数
                updateMetadataOnly(userId, chatId, msgId, fromUserId, timestamp);
                return;
            }
            
            // 2. 构建元数据JSON（不含未读数）
            JSONObject metadata = new JSONObject();
            metadata.set("m", msgId);      // msgId
            metadata.set("t", timestamp);  // time
            metadata.set("f", fromUserId); // from
            
            String jsonValue = metadata.toString();
            int originalSize = jsonValue.length();
            
            // 3. LZ4压缩
            String compressedValue = CompressionUtil.compressToBase64(jsonValue);
            int compressedSize = compressedValue.length();
            
            // 4. 保存元数据到 {chatId}:meta（使用StringCodec保持一致性）
            redissonUtils.setHashWithStringCodec(redisKey, chatId + META_SUFFIX, compressedValue);
            
            // 5. 【原子递增未读数】到 {chatId}:unread（使用Lua脚本保证原子性）
            Long newUnread = redissonUtils.executeLuaScriptAsLongWithStringCodec(
                LUA_INCR_UNREAD, 
                java.util.Collections.singletonList(redisKey), 
                chatId + UNREAD_SUFFIX
            );
            
            double ratio = CompressionUtil.compressionRatio(originalSize, compressedSize);
            
            log.info("更新会话列表元数据（时间戳防护）, userId: {}, chatId: {}, msgId: {}, unread: {}, msgTs: {}, 压缩: {}B->{}B ({}%)",
                userId, chatId, msgId, newUnread, timestamp, originalSize, compressedSize, String.format("%.1f", ratio));
                
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
        
        String compressedValue = CompressionUtil.compressToBase64(metadata.toString());
        redissonUtils.setHashWithStringCodec(redisKey, chatId + META_SUFFIX, compressedValue);
        
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
            
            // 解压并合并元数据和未读数
            Map<String, String> result = new HashMap<>();
            Map<String, String> unreadMap = new HashMap<>();
            
            for (Map.Entry<String, String> entry : allFields.entrySet()) {
                String field = entry.getKey();
                String value = entry.getValue();
                
                if (field.endsWith(META_SUFFIX)) {
                    // 元数据字段
                    String chatId = field.substring(0, field.length() - META_SUFFIX.length());
                    try {
                        String decompressed = CompressionUtil.decompressFromBase64(value);
                        result.put(chatId, decompressed);
                    } catch (Exception e) {
                        log.error("解压会话元数据失败, userId: {}, chatId: {}", userId, chatId, e);
                    }
                } else if (field.endsWith(UNREAD_SUFFIX)) {
                    // 未读数字段
                    String chatId = field.substring(0, field.length() - UNREAD_SUFFIX.length());
                    unreadMap.put(chatId, value);
                }
                // 忽略 clear_ts 字段
            }
            
            // 合并未读数到元数据JSON中
            for (Map.Entry<String, String> entry : result.entrySet()) {
                String chatId = entry.getKey();
                String metaJson = entry.getValue();
                String unread = unreadMap.getOrDefault(chatId, "0");
                
                try {
                    JSONObject metadata = JSONUtil.parseObj(metaJson);
                    metadata.set("u", Long.parseLong(unread));
                    result.put(chatId, metadata.toString());
                } catch (Exception e) {
                    log.error("合并未读数失败, userId: {}, chatId: {}", userId, chatId, e);
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
            // 获取元数据（使用StringCodec）
            String compressed = redissonUtils.getHashWithStringCodec(redisKey, chatId + META_SUFFIX);
            if (compressed == null) {
                return null;
            }
            
            String metaJson = CompressionUtil.decompressFromBase64(compressed);
            
            // 获取未读数（使用StringCodec）
            String unreadStr = redissonUtils.getHashWithStringCodec(redisKey, chatId + UNREAD_SUFFIX);
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
            
            // 1. 清零未读数
            map.put(chatId + UNREAD_SUFFIX, "0");
            
            // 2. 【关键】记录清零时间戳，防止旧消息递增
            long clearTimestamp = System.currentTimeMillis();
            map.put(chatId + CLEAR_TS_SUFFIX, String.valueOf(clearTimestamp));
            
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
            // 删除元数据、未读数、清零时间戳三个字段（使用StringCodec）
            redissonUtils.deleteHashWithStringCodec(redisKey, chatId + META_SUFFIX);
            redissonUtils.deleteHashWithStringCodec(redisKey, chatId + UNREAD_SUFFIX);
            redissonUtils.deleteHashWithStringCodec(redisKey, chatId + CLEAR_TS_SUFFIX);
            
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
