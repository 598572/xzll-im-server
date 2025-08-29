package com.xzll.business.service;

import com.xzll.business.entity.es.ImC2CMsgRecordES;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息ES查询服务接口
 */
public interface ImC2CMsgRecordESQueryService {

    /**
     * 根据会话ID查询消息记录
     * @param chatId 会话ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> findByChatId(String chatId, Pageable pageable);

    /**
     * 根据发送者ID查询消息记录
     * @param fromUserId 发送者ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> findByFromUserId(String fromUserId, Pageable pageable);

    /**
     * 根据接收者ID查询消息记录
     * @param toUserId 接收者ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> findByToUserId(String toUserId, Pageable pageable);

    /**
     * 根据消息内容进行模糊搜索
     * @param content 搜索内容
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> searchByContent(String content, Pageable pageable);

    /**
     * 根据会话ID和消息内容进行组合搜索
     * @param chatId 会话ID
     * @param content 搜索内容
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> searchByChatIdAndContent(String chatId, String content, Pageable pageable);

    /**
     * 根据时间范围查询消息记录
     * @param chatId 会话ID
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> findByTimeRange(String chatId, Long startTime, Long endTime, Pageable pageable);

    /**
     * 根据消息状态查询
     * @param chatId 会话ID
     * @param msgStatus 消息状态
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> findByMsgStatus(String chatId, Integer msgStatus, Pageable pageable);

    /**
     * 复合查询：支持多条件组合查询
     * @param chatId 会话ID（可选）
     * @param fromUserId 发送者ID（可选）
     * @param toUserId 接收者ID（可选）
     * @param content 消息内容（可选，支持模糊搜索）
     * @param msgStatus 消息状态（可选）
     * @param startTime 开始时间戳（可选）
     * @param endTime 结束时间戳（可选）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> complexSearch(String chatId, String fromUserId, String toUserId, 
                                         String content, Integer msgStatus, Long startTime, 
                                         Long endTime, Pageable pageable);
} 