package com.xzll.business.service;

import cn.hutool.core.util.StrUtil;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 消息查询路由服务（智能路由版本）
 * 根据查询条件和配置自动选择最优数据源
 *
 * 路由策略:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  查询条件       │  ES开启  │  ES关闭                             │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  有chatId      │  MongoDB │  MongoDB（分片键，性能最优）         │
 * │  无chatId      │  ES      │  MongoDB（跨分片查询，性能较差）     │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 路由说明：
 * 1. 有chatId：走MongoDB（分片键，单分片查询）
 * 2. 无chatId + ES开启：走ES（避免MongoDB跨分片scatter-gather）
 * 3. 无chatId + ES关闭：走MongoDB（兜底）
 * 4. 一次查询只使用一种数据源，不会混合查询
 * 5. 支持运行时动态切换（通过Nacos配置刷新）
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
public class MessageQueryRouter {

    @Resource
    private ImC2CMsgRecordService mongoQueryService;

    @Resource
    private ImC2CMsgRecordESQueryService esQueryService;

    /**
     * ES配置是否启用（通过环境变量或配置中心动态控制）
     * 可以通过 @Value("${im.elasticsearch.sync-enabled:false}") 注入
     */
    @org.springframework.beans.factory.annotation.Value("${im.elasticsearch.sync-enabled:false}")
    private Boolean esSyncEnabled;

    /**
     * 数据来源标识
     */
    public static final String SOURCE_MONGODB = "MongoDB";
    public static final String SOURCE_ES = "ES";

    /**
     * 判断是否启用ES
     */
    private boolean isESEnabled() {
        return esSyncEnabled != null && esSyncEnabled;
    }

    /**
     * 根据会话ID查询消息（有chatId，直接走MongoDB）
     *
     * @param chatId   会话ID
     * @param page     页码
     * @param size     每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> findByChatId(String chatId, int page, int size) {
        long startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        log.info("路由查询: chatId={} -> MongoDB（分片键查询）", chatId);

        // 有chatId，直接走MongoDB
        Page<ImC2CMsgRecordES> result = mongoQueryService.queryByChatId(chatId, pageable);

        long costTime = System.currentTimeMillis() - startTime;
        log.info("查询完成: 数据源=MongoDB, chatId={}, 耗时={}ms, 结果数={}",
            chatId, costTime, result.getTotalElements());

        return result;
    }

    /**
     * 根据发送者ID查询消息（无chatId，根据ES配置智能路由）
     *
     * @param fromUserId 发送者ID
     * @param page       页码
     * @param size       每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> findByFromUserId(String fromUserId, int page, int size) {
        long startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        Page<ImC2CMsgRecordES> result;
        long costTime;

        if (isESEnabled()) {
            log.info("路由查询: fromUserId={} -> ES（避免跨分片查询）", fromUserId);
            result = esQueryService.findByFromUserId(fromUserId, pageable);
            costTime = System.currentTimeMillis() - startTime;
            log.info("查询完成: 数据源=ES, fromUserId={}, 耗时={}ms, 结果数={}",
                fromUserId, costTime, result.getTotalElements());
        } else {
            log.warn("路由查询: fromUserId={} -> MongoDB（跨分片查询，性能较差）", fromUserId);
            result = mongoQueryService.queryByFromUserId(fromUserId, pageable);
            costTime = System.currentTimeMillis() - startTime;
            log.info("查询完成: 数据源=MongoDB, fromUserId={}, 耗时={}ms, 结果数={}",
                fromUserId, costTime, result.getTotalElements());
        }

        return result;
    }

    /**
     * 根据接收者ID查询消息（无chatId，根据ES配置智能路由）
     *
     * @param toUserId 接收者ID
     * @param page     页码
     * @param size     每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> findByToUserId(String toUserId, int page, int size) {
        long startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        Page<ImC2CMsgRecordES> result;
        long costTime;

        if (isESEnabled()) {
            log.info("路由查询: toUserId={} -> ES（避免跨分片查询）", toUserId);
            result = esQueryService.findByToUserId(toUserId, pageable);
            costTime = System.currentTimeMillis() - startTime;
            log.info("查询完成: 数据源=ES, toUserId={}, 耗时={}ms, 结果数={}",
                toUserId, costTime, result.getTotalElements());
        } else {
            log.warn("路由查询: toUserId={} -> MongoDB（跨分片查询，性能较差）", toUserId);
            result = mongoQueryService.queryByToUserId(toUserId, pageable);
            costTime = System.currentTimeMillis() - startTime;
            log.info("查询完成: 数据源=MongoDB, toUserId={}, 耗时={}ms, 结果数={}",
                toUserId, costTime, result.getTotalElements());
        }

        return result;
    }

    /**
     * 根据消息内容搜索（无chatId，根据ES配置智能路由）
     *
     * @param content 搜索内容
     * @param page    页码
     * @param size    每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> searchByContent(String content, int page, int size) {
        long startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        Page<ImC2CMsgRecordES> result;
        long costTime;

        if (isESEnabled()) {
            log.info("路由查询: content={} -> ES（全文搜索）", content);
            result = esQueryService.searchByContent(content, pageable);
            costTime = System.currentTimeMillis() - startTime;
            log.info("查询完成: 数据源=ES, content={}, 耗时={}ms, 结果数={}",
                content, costTime, result.getTotalElements());
        } else {
            log.warn("路由查询: content={} -> MongoDB（模糊查询，性能较差）", content);
            result = mongoQueryService.queryByContent(content, pageable);
            costTime = System.currentTimeMillis() - startTime;
            log.info("查询完成: 数据源=MongoDB, content={}, 耗时={}ms, 结果数={}",
                content, costTime, result.getTotalElements());
        }

        return result;
    }

    /**
     * 根据会话ID和内容搜索（有chatId，直接走MongoDB）
     *
     * @param chatId 会话ID
     * @param content 搜索内容
     * @param page   页码
     * @param size   每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> searchByChatIdAndContent(String chatId, String content, int page, int size) {
        long startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        log.info("路由查询: chatId={}, content={} -> MongoDB（分片键查询）", chatId, content);

        // 有chatId，直接走MongoDB
        Page<ImC2CMsgRecordES> result = mongoQueryService.queryByChatIdAndContent(chatId, content, pageable);

        long costTime = System.currentTimeMillis() - startTime;
        log.info("查询完成: 数据源=MongoDB, chatId={}, content={}, 耗时={}ms, 结果数={}",
            chatId, content, costTime, result.getTotalElements());

        return result;
    }

    /**
     * 根据时间范围查询（有chatId，直接走MongoDB）
     *
     * @param chatId    会话ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码
     * @param size      每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> findByTimeRange(String chatId, Long startTime, Long endTime, int page, int size) {
        long queryStartTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        log.info("路由查询: chatId={}, startTime={}, endTime={} -> MongoDB（分片键查询）",
            chatId, startTime, endTime);

        // 有chatId，直接走MongoDB
        Page<ImC2CMsgRecordES> result = mongoQueryService.queryByTimeRange(chatId, startTime, endTime, pageable);

        long costTime = System.currentTimeMillis() - queryStartTime;
        log.info("查询完成: 数据源=MongoDB, chatId={}, 耗时={}ms, 结果数={}",
            chatId, costTime, result.getTotalElements());

        return result;
    }

    /**
     * 根据消息状态查询（有chatId，直接走MongoDB）
     *
     * @param chatId    会话ID
     * @param msgStatus 消息状态
     * @param page      页码
     * @param size      每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> findByMsgStatus(String chatId, Integer msgStatus, int page, int size) {
        long startTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        log.info("路由查询: chatId={}, msgStatus={} -> MongoDB（分片键查询）",
            chatId, msgStatus);

        // 有chatId，直接走MongoDB
        Page<ImC2CMsgRecordES> result = mongoQueryService.queryByMsgStatus(chatId, msgStatus, pageable);

        long costTime = System.currentTimeMillis() - startTime;
        log.info("查询完成: 数据源=MongoDB, chatId={}, msgStatus={}, 耗时={}ms, 结果数={}",
            chatId, msgStatus, costTime, result.getTotalElements());

        return result;
    }

    /**
     * 复合查询（智能路由）
     *
     * @param chatId     会话ID（可选）
     * @param fromUserId 发送者ID（可选）
     * @param toUserId   接收者ID（可选）
     * @param content    消息内容（可选）
     * @param msgStatus  消息状态（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param page       页码
     * @param size       每页数量
     * @return 查询结果
     */
    public Page<ImC2CMsgRecordES> complexSearch(
            String chatId, String fromUserId, String toUserId,
            String content, Integer msgStatus, Long startTime, Long endTime,
            int page, int size) {

        long queryStartTime = System.currentTimeMillis();
        Pageable pageable = PageRequest.of(page, size);

        // 判断是否有chatId
        boolean hasChatId = StrUtil.isNotBlank(chatId);

        Page<ImC2CMsgRecordES> result;
        String dataSource;
        long costTime;

        if (hasChatId) {
            // 有chatId：走MongoDB（分片键，单分片查询，性能最优）
            log.info("路由查询: 有chatId({}) -> MongoDB（分片键查询）", chatId);
            result = mongoQueryService.queryComplex(chatId, fromUserId, toUserId, content,
                msgStatus, startTime, endTime, pageable);
            dataSource = SOURCE_MONGODB;
        } else if (isESEnabled()) {
            // 无chatId + ES开启：走ES（避免MongoDB跨分片scatter-gather）
            log.info("路由查询: 无chatId + ES开启 -> ES（避免跨分片查询）");
            result = esQueryService.complexSearch(chatId, fromUserId, toUserId, content,
                msgStatus, startTime, endTime, pageable);
            dataSource = SOURCE_ES;
        } else {
            // 无chatId + ES关闭：走MongoDB（兜底，会有性能警告）
            log.warn("路由查询: 无chatId + ES关闭 -> MongoDB（跨分片查询，性能较差）");
            result = mongoQueryService.queryComplex(chatId, fromUserId, toUserId, content,
                msgStatus, startTime, endTime, pageable);
            dataSource = SOURCE_MONGODB;
        }

        costTime = System.currentTimeMillis() - queryStartTime;
        log.info("查询完成: 数据源={}, chatId={}, fromUserId={}, toUserId={}, 耗时={}ms, 结果数={}",
            dataSource, chatId, fromUserId, toUserId, costTime, result.getTotalElements());

        return result;
    }

    /**
     * 获取当前使用的数据源
     */
    public String getCurrentDataSource() {
        return isESEnabled() ? SOURCE_ES : SOURCE_MONGODB;
    }
}
