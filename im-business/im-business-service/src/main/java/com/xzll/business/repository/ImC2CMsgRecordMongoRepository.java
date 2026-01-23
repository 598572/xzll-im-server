package com.xzll.business.repository;

import com.xzll.business.entity.mongo.ImC2CMsgRecordMongo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: C2C消息记录 MongoDB Repository
 * 
 * 功能说明：
 * - 提供基本的 CRUD 操作
 * - 支持按会话ID查询消息
 * - 支持按用户ID查询消息
 * - 支持分页和排序
 */
@Repository
public interface ImC2CMsgRecordMongoRepository extends MongoRepository<ImC2CMsgRecordMongo, String> {

    /**
     * 根据消息ID查询消息
     * 
     * @param msgId 消息ID
     * @return 消息记录
     */
    Optional<ImC2CMsgRecordMongo> findByMsgId(String msgId);

    /**
     * 根据会话ID查询消息列表（按时间倒序）
     * 
     * @param chatId 会话ID
     * @param pageable 分页参数
     * @return 消息列表
     */
    List<ImC2CMsgRecordMongo> findByChatIdOrderByMsgCreateTimeDesc(String chatId, Pageable pageable);

    /**
     * 根据会话ID查询消息列表（按时间正序）
     * 
     * @param chatId 会话ID
     * @param pageable 分页参数
     * @return 消息列表
     */
    List<ImC2CMsgRecordMongo> findByChatIdOrderByMsgCreateTimeAsc(String chatId, Pageable pageable);

    /**
     * 根据会话ID和时间范围查询消息（倒序）
     * 
     * @param chatId 会话ID
     * @param startTime 开始时间（毫秒）
     * @param endTime 结束时间（毫秒）
     * @param pageable 分页参数
     * @return 消息列表
     */
    @Query("{'chatId': ?0, 'msgCreateTime': {$gte: ?1, $lte: ?2}}")
    List<ImC2CMsgRecordMongo> findByChatIdAndTimeRange(String chatId, Long startTime, Long endTime, Pageable pageable);

    /**
     * 根据会话ID查询最后一条消息
     * 
     * @param chatId 会话ID
     * @return 最后一条消息
     */
    Optional<ImC2CMsgRecordMongo> findFirstByChatIdOrderByMsgCreateTimeDesc(String chatId);

    /**
     * 根据发送者ID查询消息
     * 
     * @param fromUserId 发送者ID
     * @param pageable 分页参数
     * @return 消息列表
     */
    List<ImC2CMsgRecordMongo> findByFromUserIdOrderByMsgCreateTimeDesc(String fromUserId, Pageable pageable);

    /**
     * 根据接收者ID查询消息
     * 
     * @param toUserId 接收者ID
     * @param pageable 分页参数
     * @return 消息列表
     */
    List<ImC2CMsgRecordMongo> findByToUserIdOrderByMsgCreateTimeDesc(String toUserId, Pageable pageable);

    /**
     * 根据会话ID和消息ID查询，用于分页游标
     * 查询比指定时间更早的消息
     * 
     * @param chatId 会话ID
     * @param msgCreateTime 时间游标
     * @param pageable 分页参数
     * @return 消息列表
     */
    @Query("{'chatId': ?0, 'msgCreateTime': {$lt: ?1}}")
    List<ImC2CMsgRecordMongo> findByChatIdAndMsgCreateTimeLessThan(String chatId, Long msgCreateTime, Pageable pageable);

    /**
     * 根据会话ID和消息ID查询，用于分页游标
     * 查询比指定时间更新的消息
     * 
     * @param chatId 会话ID
     * @param msgCreateTime 时间游标
     * @param pageable 分页参数
     * @return 消息列表
     */
    @Query("{'chatId': ?0, 'msgCreateTime': {$gt: ?1}}")
    List<ImC2CMsgRecordMongo> findByChatIdAndMsgCreateTimeGreaterThan(String chatId, Long msgCreateTime, Pageable pageable);

    /**
     * 统计会话的消息数量
     * 
     * @param chatId 会话ID
     * @return 消息数量
     */
    long countByChatId(String chatId);

    /**
     * 查询用户参与的所有会话的最新消息
     * 使用聚合查询（由 Service 层实现）
     */

    /**
     * 根据多个会话ID批量查询最新消息
     * 
     * @param chatIds 会话ID列表
     * @return 消息列表
     */
    @Query("{'chatId': {$in: ?0}}")
    List<ImC2CMsgRecordMongo> findByChatIdIn(List<String> chatIds);

    /**
     * 全文搜索消息内容
     * 
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 消息列表
     */
    @Query("{'msgContent': {$regex: ?0, $options: 'i'}}")
    List<ImC2CMsgRecordMongo> searchByMsgContent(String keyword, Pageable pageable);

    /**
     * 在指定会话中搜索消息内容
     * 
     * @param chatId 会话ID
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 消息列表
     */
    @Query("{'chatId': ?0, 'msgContent': {$regex: ?1, $options: 'i'}}")
    List<ImC2CMsgRecordMongo> searchByChatIdAndMsgContent(String chatId, String keyword, Pageable pageable);

    /**
     * 查询最新的N条消息（用于控制台查询）
     * 
     * @param pageable 分页参数（包含排序和数量限制）
     * @return 消息列表
     */
    @Query("{}")
    List<ImC2CMsgRecordMongo> findLatestMessages(Pageable pageable);
}
