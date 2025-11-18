package com.xzll.connect.service.impl;

import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.ProtoResponseCode;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.service.C2CMsgRetryService;
import com.xzll.connect.service.dto.C2CMsgRetryEvent;
import com.xzll.grpc.C2CMsgPush;
import com.xzll.grpc.ImProtoResponse;
import com.xzll.grpc.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * C2C消息重试服务实现类
 * 使用Redis ZSet实现延迟队列，定时任务扫描到期消息
 *
 * ps: 此机制是消息可靠性重要一环！提供了服务端消息重推的机制
 *
 * @Author: hzz
 * @Date: 2025-11-14
 */
@Slf4j
@Service
public class C2CMsgRetryServiceImpl implements C2CMsgRetryService {
    
    private static final String TAG = "[C2C消息重试服务]";
    
    @Resource
    private RedissonUtils redissonUtils;
    
    @Resource
    private C2CMsgProvider c2CMsgProvider;
    
    // 基础配置
    @Value("${im-server.c2c.retry.enabled:true}")
    private boolean retryEnabled;
    
    @Value("${im-server.c2c.retry.max-retries:3}")
    private int maxRetries;
    
    @Value("${im-server.c2c.retry.delays:5,30,300}")
    private String delaysConfig;
    
    @Value("${im-server.c2c.retry.batch-size:10000}")
    private int batchSize; // 每次扫描最多处理的消息数量
    
    @Value("${im-server.c2c.retry.scan-interval:1000}")
    private long scanInterval; // 定时任务扫描间隔（毫秒），默认1000ms（1秒）
    
    // 动态配置
    private int[] retryDelays;
    
    // 重试处理线程池
    private final ExecutorService retryExecutor = Executors.newFixedThreadPool(20);
    
    // Lua脚本
    private static final String LUA_ADD_TO_RETRY_QUEUE = "lua/add_to_retry_queue.lua";
    private static final String LUA_REMOVE_FROM_RETRY_QUEUE = "lua/remove_from_retry_queue.lua";
    
    private String addToRetryQueueScript;
    private String removeFromRetryQueueScript;
    
    /**
     * 初始化配置参数和Lua脚本
     */
    @PostConstruct
    public void initConfig() {
        // 解析重试延迟配置
        String[] delayStrs = delaysConfig.split(",");
        retryDelays = new int[delayStrs.length];
        for (int i = 0; i < delayStrs.length; i++) {
            retryDelays[i] = Integer.parseInt(delayStrs[i].trim());
        }
        
        // 加载Lua脚本
        try {
            addToRetryQueueScript = loadLuaScript(LUA_ADD_TO_RETRY_QUEUE);
            removeFromRetryQueueScript = loadLuaScript(LUA_REMOVE_FROM_RETRY_QUEUE);
            log.info("{}Lua脚本加载完成", TAG);
        } catch (Exception e) {
            log.error("{}Lua脚本加载失败", TAG, e);
            throw new RuntimeException("Lua脚本加载失败", e);
        }
        
        log.info("{}配置初始化完成 - 重试次数: {}, 延迟: {}s, 批量大小: {}, 扫描间隔: {}ms", 
            TAG, maxRetries, Arrays.toString(retryDelays), batchSize, scanInterval);
    }
    
    /**
     * 加载Lua脚本
     */
    private String loadLuaScript(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        InputStream in = resource.getInputStream();
        return IOUtils.toString(in, StandardCharsets.UTF_8);
    }
    
    /**
     * 添加消息到延迟队列（等待客户端（接收方）ACK）
     * 使用Lua脚本保证原子性：同时添加到ZSet和Hash
     * 在C2CMsgSendProtoStrategyImpl.exchange中调用
     */
    @Override
    public void addToRetryQueue(C2CSendMsgAO packet) {
        if (!retryEnabled) {
            log.debug("{}重试机制未启用，跳过 - clientMsgId: {}", TAG, packet.getClientMsgId());
            return;
        }
        
        try {
            // 构建重试事件
            C2CMsgRetryEvent retryEvent = buildRetryEvent(packet);
            String value = JSONUtil.toJsonStr(retryEvent);
            
            // 计算执行时间戳（当前时间 + 延迟时间）
            long executeTime = System.currentTimeMillis() + retryDelays[0] * 1000;
            
            //  使用Lua脚本原子性添加（同时添加到ZSet和Hash）
            // 使用 msgId（雪花算法）作为 Hash 的 key
            Long result = redissonUtils.executeLuaScriptAsLong(
                addToRetryQueueScript,
                Arrays.asList(
                    ImConstant.RedisKeyConstant.C2C_MSG_RETRY_QUEUE,
                    ImConstant.RedisKeyConstant.C2C_MSG_RETRY_INDEX
                ),
                value,
                String.valueOf(executeTime),
                packet.getMsgId()
            );
            
            if (result > 0) {
                log.info("{}消息已添加到延迟队列 - clientMsgId: {}, msgId: {}, 执行时间: {}ms后", 
                    TAG, packet.getClientMsgId(), packet.getMsgId(), retryDelays[0] * 1000);
            } else {
                log.warn("{}消息添加到延迟队列失败 - clientMsgId: {}, msgId: {}", 
                    TAG, packet.getClientMsgId(), packet.getMsgId());
            }
        } catch (Exception e) {
            log.error("{}添加消息到延迟队列异常 - clientMsgId: {}, msgId: {}", 
                TAG, packet.getClientMsgId(), packet.getMsgId(), e);
        }
    }
    
    /**
     * 从延迟队列删除消息（收到客户端ACK时）
     * 使用Lua脚本保证原子性：同时从ZSet和Hash删除
     * 
     * @param msgId 服务端消息ID（雪花算法）
     */
    @Override
    public void removeFromRetryQueue(String msgId) {
        try {
            //  使用Lua脚本原子性删除（同时从ZSet和Hash删除）
            // 使用 msgId（雪花算法）作为 Hash 的 key
            Long result = redissonUtils.executeLuaScriptAsLong(
                removeFromRetryQueueScript,
                Arrays.asList(
                    ImConstant.RedisKeyConstant.C2C_MSG_RETRY_QUEUE,
                    ImConstant.RedisKeyConstant.C2C_MSG_RETRY_INDEX
                ),
                msgId
            );
            
            if (result > 0) {
                log.info("{}收到客户端ACK，从延迟队列删除消息 - msgId: {}", TAG, msgId);
            } else {
                log.debug("{}重试消息不存在（可能已过期或已处理） - msgId: {}", TAG, msgId);
            }
        } catch (Exception e) {
            log.error("{}从延迟队列删除消息异常 - msgId: {}", TAG, msgId, e);
        }
    }
    
    /**
     * 定时扫描延迟队列
     * 扫描"到期消息"（执行时间戳 <= 当前时间的消息）
     * 
     * 说明：
     * - "到期消息"：指执行时间戳（score）小于等于当前时间的消息
     * - 例如：消息在 10:00:00 添加到队列，延迟 5 秒，执行时间戳为 10:00:05
     * - 当定时任务在 10:00:05 及之后执行时，该消息就是"到期消息"
     * - 使用 ZRANGEBYSCORE 只获取到期的消息，性能极高（O(log N + M)，M是到期消息数）
     * - 使用 LIMIT 限制每次处理的消息数量，避免一次性处理太多消息导致系统压力过大
     * - 扫描间隔可通过配置项 im-server.c2c.retry.scan-interval 配置（单位：毫秒，默认1000ms）
     */
    @Scheduled(fixedRateString = "${im-server.c2c.retry.scan-interval:1000}")
    public void scanRetryQueue() {
        if (!retryEnabled) {
            return;
        }
        try {
            long currentTime = System.currentTimeMillis();
            
            //  关键优化1：只获取到期的消息（score <= 当前时间）
            //  关键优化2：使用 LIMIT 限制每次处理的消息数量，避免一次性处理太多
            // 即使有大量消息到期，也分批处理，避免系统压力过大
            Collection<String> expiredValues = redissonUtils.getZSetRangeByScore(
                ImConstant.RedisKeyConstant.C2C_MSG_RETRY_QUEUE, 0L, currentTime, batchSize);
            
            if (expiredValues == null || expiredValues.isEmpty()) {
                return; // 没有到期的消息，直接返回
            }
            
            int processedCount = expiredValues.size();
            log.debug("{}扫描到{}条到期消息（本次处理上限: {}）", TAG, processedCount, batchSize);
            
            // 如果达到批量上限，记录警告（可能有更多消息待处理）
            if (processedCount >= batchSize) {
                log.warn("{}本次扫描达到批量上限{}，可能还有更多到期消息待处理，将在下次扫描时处理", 
                    TAG, batchSize);
            }
            
            //  优化：按 toUserId 分组，批量处理同一用户的消息
            Map<String, List<C2CMsgRetryEvent>> groupedByUserId = expiredValues.stream()
                .map(value -> {
                    try {
                        C2CMsgRetryEvent retryEvent = JSONUtil.toBean(value, C2CMsgRetryEvent.class);
                        // 保存原始 value，用于从ZSet删除
                        retryEvent.setOriginalValue(value);
                        return retryEvent;
                    } catch (Exception e) {
                        log.error("{}解析到期消息异常: {}", TAG, value, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(C2CMsgRetryEvent::getToUserId));
            
            log.debug("{}按用户分组完成，共{}个用户，{}条消息", TAG, groupedByUserId.size(), processedCount);
            
            // 按用户分组批量处理
            for (Map.Entry<String, List<C2CMsgRetryEvent>> entry : groupedByUserId.entrySet()) {
                String toUserId = entry.getKey();
                List<C2CMsgRetryEvent> userRetryEvents = entry.getValue();
                
                // 异步处理每个用户的消息（避免阻塞定时任务）
                CompletableFuture.runAsync(() -> {
                    processRetryBatch(toUserId, userRetryEvents);
                }, retryExecutor);
            }
        } catch (Exception e) {
            log.error("{}扫描延迟队列异常", TAG, e);
        }
    }
    
    /**
     * 批量处理重试（按用户分组）
     * 优化：同一用户的消息只检查一次在线状态，只查找一次 Channel，然后批量发送
     */
    private void processRetryBatch(String toUserId, List<C2CMsgRetryEvent> retryEvents) {
        if (retryEvents == null || retryEvents.isEmpty()) {
            return;
        }
        
        try {
            // 1. 统一检查接收人是否在线（只检查一次）
            Channel targetChannel = LocalChannelManager.getChannelByUserId(toUserId);
            String userStatus = redissonUtils.getHash(
                ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, 
                toUserId
            );
            
            boolean isOnline = targetChannel != null && Objects.equals(
                ImConstant.UserStatus.ON_LINE.getValue().toString(), userStatus);
            
            // 2. 批量处理消息
            List<C2CMsgRetryEvent> needRetry = new ArrayList<>();
            List<C2CMsgRetryEvent> needOffline = new ArrayList<>();
            
            for (C2CMsgRetryEvent retryEvent : retryEvents) {
                try {
                    // 2.1 从ZSet删除（避免重复处理）
                    if (retryEvent.getOriginalValue() != null) {
                        redissonUtils.removeZSetValue(
                            ImConstant.RedisKeyConstant.C2C_MSG_RETRY_QUEUE, 
                            retryEvent.getOriginalValue()
                        );
                    }
                    
                    // 2.2 检查是否已收到客户端ACK（使用 msgId 作为 Hash 的 key）
                    String indexValue = redissonUtils.getHash(
                        ImConstant.RedisKeyConstant.C2C_MSG_RETRY_INDEX, 
                        retryEvent.getMsgId()
                    );
                    if (indexValue == null) {
                        log.debug("{}消息已收到客户端ACK，取消重试 - clientMsgId: {}, msgId: {}", 
                            TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId());
                        continue; // 已收到ACK，跳过
                    }
                    
                    // 2.3 检查重试次数
                    if (retryEvent.getRetryCount() >= maxRetries) {
                        log.warn("{}消息重试超过{}次，改为离线消息 - clientMsgId: {}, msgId: {}", 
                            TAG, maxRetries, retryEvent.getClientMsgId(), retryEvent.getMsgId());
                        needOffline.add(retryEvent);
                        continue;
                    }
                    
                    // 2.4 根据在线状态分类
                    if (isOnline) {
                        needRetry.add(retryEvent);
                    } else {
                        log.warn("{}重试时接收人已离线，改为离线消息 - clientMsgId: {}, msgId: {}", 
                            TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId());
                        needOffline.add(retryEvent);
                    }
                } catch (Exception e) {
                    log.error("{}处理重试事件异常 - clientMsgId: {}, msgId: {}", 
                        TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), e);
                }
            }
            
            // 3. 批量发送需要重试的消息（同一用户，批量发送）
            if (!needRetry.isEmpty() && targetChannel != null) {
                log.info("{}批量重试发送消息 - toUserId: {}, 消息数: {}", TAG, toUserId, needRetry.size());
                for (C2CMsgRetryEvent retryEvent : needRetry) {
                    sendProtoMsg(targetChannel, retryEvent);
                    
                    // 更新重试次数并重新添加到延迟队列
                    int nextRetryCount = retryEvent.getRetryCount() + 1;
                    retryEvent.setRetryCount(nextRetryCount);
                    retryEvent.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    
                    // 计算下次执行时间
                    long executeTime = System.currentTimeMillis() + retryDelays[nextRetryCount] * 1000;
                    
                    //  使用Lua脚本原子性重新添加（使用 msgId 作为 Hash 的 key）
                    String newValue = JSONUtil.toJsonStr(retryEvent);
                    redissonUtils.executeLuaScriptAsLong(
                        addToRetryQueueScript,
                        Arrays.asList(
                            ImConstant.RedisKeyConstant.C2C_MSG_RETRY_QUEUE,
                            ImConstant.RedisKeyConstant.C2C_MSG_RETRY_INDEX
                        ),
                        newValue,
                        String.valueOf(executeTime),
                        retryEvent.getMsgId()  // 使用 msgId（雪花算法
                    );
                    
                    log.debug("{}消息重试成功，已添加下次重试任务 - clientMsgId: {}, msgId: {}, 重试次数: {}, 下次延迟: {}s", 
                        TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), nextRetryCount, retryDelays[nextRetryCount]);
                }
            }
            
            // 4. 批量标记为离线消息
            if (!needOffline.isEmpty()) {
                log.info("{}批量标记为离线消息 - toUserId: {}, 消息数: {}", TAG, toUserId, needOffline.size());
                for (C2CMsgRetryEvent retryEvent : needOffline) {
                    markAsOffline(retryEvent);
                }
            }
            
        } catch (Exception e) {
            log.error("{}批量处理重试异常 - toUserId: {}, 消息数: {}", TAG, toUserId, retryEvents.size(), e);
        }
    }
    
    /**
     * 改为离线消息
     */
    private void markAsOffline(C2CMsgRetryEvent retryEvent) {
        try {
            // 1. 删除Hash索引（使用 msgId 作为 Hash 的 key）
            redissonUtils.deleteHash(ImConstant.RedisKeyConstant.C2C_MSG_RETRY_INDEX, retryEvent.getMsgId());
            
            // 2. 构建离线消息
            C2COffLineMsgAO offLineMsg = C2COffLineMsgAO.builder()
                .clientMsgId(retryEvent.getClientMsgId())
                .fromUserId(retryEvent.getFromUserId())
                .toUserId(retryEvent.getToUserId())
                .msgStatus(MsgStatusEnum.MsgStatus.OFF_LINE.getCode())
                .msgContent(retryEvent.getMsgContent())
                .msgFormat(retryEvent.getMsgFormat())
                .build();
            offLineMsg.setMsgId(retryEvent.getMsgId());
            offLineMsg.setChatId(retryEvent.getChatId());
            offLineMsg.setMsgCreateTime(retryEvent.getMsgCreateTime());
            
            // 3. 发送离线消息
            c2CMsgProvider.offLineMsg(offLineMsg);
            
            log.info("{}消息已改为离线消息 - clientMsgId: {}, msgId: {}", 
                TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId());
        } catch (Exception e) {
            log.error("{}改为离线消息异常 - clientMsgId: {}, msgId: {}", 
                TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), e);
        }
    }
    
    /**
     * 构建重试事件
     */
    private C2CMsgRetryEvent buildRetryEvent(C2CSendMsgAO packet) {
        C2CMsgRetryEvent retryEvent = new C2CMsgRetryEvent();
        retryEvent.setClientMsgId(packet.getClientMsgId());
        retryEvent.setMsgId(packet.getMsgId());
        retryEvent.setFromUserId(packet.getFromUserId());
        retryEvent.setToUserId(packet.getToUserId());
        retryEvent.setChatId(packet.getChatId());
        retryEvent.setRetryCount(0);
        retryEvent.setMsgContent(packet.getMsgContent());
        retryEvent.setMsgFormat(packet.getMsgFormat());
        retryEvent.setMsgCreateTime(packet.getMsgCreateTime());
        retryEvent.setMaxRetries(maxRetries);
        retryEvent.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return retryEvent;
    }
    
    /**
     * 构建推送消息响应
     */
    private C2CMsgPush buildPushMsgResp(C2CMsgRetryEvent retryEvent) {
        return C2CMsgPush.newBuilder()
            .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(retryEvent.getClientMsgId()))
            .setMsgId(ProtoConverterUtil.snowflakeStringToLong(retryEvent.getMsgId()))
            .setFrom(ProtoConverterUtil.snowflakeStringToLong(retryEvent.getFromUserId()))
            .setTo(ProtoConverterUtil.snowflakeStringToLong(retryEvent.getToUserId()))
            .setFormat(retryEvent.getMsgFormat())
            .setContent(retryEvent.getMsgContent())
            .setTime(retryEvent.getMsgCreateTime())
            .build();
    }
    
    /**
     * 发送消息到客户端
     */
    private void sendProtoMsg(Channel channel, C2CMsgRetryEvent retryEvent) {
        try {
            C2CMsgPush pushMsg = buildPushMsgResp(retryEvent);
            
            log.debug("{}【重试发送消息】开始发送消息到客户端 - clientMsgId: {}, msgId: {}, to: {}",
                TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), retryEvent.getToUserId());
            
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(MsgType.C2C_MSG_PUSH)
                .setPayload(com.google.protobuf.ByteString.copyFrom(pushMsg.toByteArray()))
                .setCode(ProtoResponseCode.SUCCESS)
                .build();
            
            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            
            channel.writeAndFlush(new BinaryWebSocketFrame(buf))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.debug("{}【重试发送成功】消息发送到客户端成功 - clientMsgId: {}, msgId: {}, to: {}",
                            TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), retryEvent.getToUserId());
                    } else {
                        log.warn("{}【重试发送失败】消息发送到客户端失败 - clientMsgId: {}, msgId: {}, to: {}, error: {}",
                            TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), retryEvent.getToUserId(), 
                            future.cause() != null ? future.cause().getMessage() : "unknown");
                    }
                });
        } catch (Exception e) {
            log.error("{}【重试发送异常】发送消息异常 - clientMsgId: {}, msgId: {}, to: {}, error: {}", 
                TAG, retryEvent.getClientMsgId(), retryEvent.getMsgId(), retryEvent.getToUserId(), e.getMessage(), e);
        }
    }
}

