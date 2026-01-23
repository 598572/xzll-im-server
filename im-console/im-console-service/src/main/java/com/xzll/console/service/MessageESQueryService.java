package com.xzll.console.service;

import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.vo.MessageSearchResultVO;

import java.util.List;
import java.util.Map;

/**
 * ES消息查询服务接口
 * 提供丰富的消息搜索能力
 * 
 * 适用场景:
 * - 按用户ID搜索（发送方/接收方）
 * - 消息内容全文搜索（中文分词）
 * - 时间范围查询
 * - 消息状态过滤
 * - 复合条件组合查询
 * - 消息统计分析
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
public interface MessageESQueryService {

    /**
     * 复合条件搜索
     * 支持多种条件组合，ES性能优化
     *
     * @param searchDTO 搜索条件
     * @return 搜索结果（分页）
     */
    MessageSearchResultVO search(MessageSearchDTO searchDTO);

    /**
     * 按发送方用户ID搜索
     *
     * @param fromUserId 发送方用户ID
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 搜索结果
     */
    MessageSearchResultVO searchByFromUserId(String fromUserId, int pageNum, int pageSize);

    /**
     * 按接收方用户ID搜索
     *
     * @param toUserId 接收方用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    MessageSearchResultVO searchByToUserId(String toUserId, int pageNum, int pageSize);

    /**
     * 按会话ID搜索（ES实现）
     * 适用于需要额外过滤条件的场景
     *
     * @param chatId   会话ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    MessageSearchResultVO searchByChatId(String chatId, int pageNum, int pageSize);

    /**
     * 消息内容全文搜索
     * 支持中文分词，高亮显示
     *
     * @param content  搜索关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize);

    /**
     * 时间范围查询
     *
     * @param startTime 开始时间（时间戳，毫秒）
     * @param endTime   结束时间（时间戳，毫秒）
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 搜索结果
     */
    MessageSearchResultVO searchByTimeRange(Long startTime, Long endTime, int pageNum, int pageSize);

    /**
     * 获取最新消息列表
     * 按消息时间倒序排列
     *
     * @param limit 数量限制
     * @return 消息列表
     */
    List<ImC2CMsgRecord> getLatestMessages(int limit);

    /**
     * 获取消息统计信息
     * 包括：总消息数、各状态消息数、各类型消息数等
     *
     * @return 统计信息
     */
    Map<String, Object> getMessageStatistics();

    /**
     * 按用户统计消息数量
     * 
     * @param userId 用户ID
     * @return 统计信息（发送数、接收数等）
     */
    Map<String, Object> getUserMessageStatistics(String userId);

    /**
     * 按会话统计消息数量
     *
     * @param chatId 会话ID
     * @return 统计信息
     */
    Map<String, Object> getChatMessageStatistics(String chatId);

    /**
     * 检查ES连接状态
     *
     * @return true-连接正常 false-连接异常
     */
    boolean isConnectionHealthy();

    /**
     * 获取索引信息
     *
     * @return 索引信息（文档数、大小等）
     */
    Map<String, Object> getIndexInfo();
}
