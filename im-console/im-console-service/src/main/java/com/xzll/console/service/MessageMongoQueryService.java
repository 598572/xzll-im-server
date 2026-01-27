package com.xzll.console.service;

import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.vo.MessageSearchResultVO;

import java.util.List;
import java.util.Map;

/**
 * MongoDB 消息查询服务接口
 * 
 * 功能说明：
 * - 替代 ES 进行消息查询
 * - 支持多条件复合查询
 * - 支持分页和排序
 * - 支持模糊搜索
 * 
 * 分片说明（重要）：
 * - 分片键: chatId（哈希分片）
 * - 查询时尽量带上 chatId 参数，避免跨分片 scatter-gather 查询
 * - 不带 chatId 的查询会扫描所有分片，性能较差
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
public interface MessageMongoQueryService {

    /**
     * 多条件复合查询
     * 
     * 支持的查询条件：
     * - chatId: 会话ID（精确匹配）【分片键 - 强烈建议带上】
     * - fromUserId: 发送者ID（精确匹配）
     * - toUserId: 接收者ID（精确匹配）
     * - content: 消息内容（模糊搜索）
     * - msgStatus: 消息状态（精确匹配）
     * - msgFormat: 消息格式（精确匹配）
     * - startTime/endTime: 时间范围
     *
     * 分片注意：
     * - 带 chatId 查询：单分片查询，性能最优
     * - 不带 chatId 查询：扫描所有分片（scatter-gather），性能较差
     *
     * @param searchDTO 搜索条件
     * @return 搜索结果
     */
    MessageSearchResultVO search(MessageSearchDTO searchDTO);

    /**
     * 按消息内容搜索（模糊匹配）
     *
     * @param content 搜索关键词
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize);

    /**
     * 获取最新消息列表
     * 
     * 注意：此方法不带分片键chatId，会触发跨分片scatter-gather查询
     * 适用场景：后台管理系统查看最新消息（低频操作）
     *
     * @param limit 数量限制
     * @return 消息列表
     */
    List<ImC2CMsgRecord> getLatestMessages(int limit);

    /**
     * 按会话ID查询消息（分片优化查询 - 推荐使用）
     * 
     * 此方法带有分片键chatId，只查询单个分片，性能最优
     *
     * @param chatId 会话ID（分片键）
     * @param limit 数量限制
     * @return 消息列表
     */
    List<ImC2CMsgRecord> getMessagesByChatId(String chatId, int limit);

    /**
     * 检查 MongoDB 连接健康状态
     *
     * @return true-连接正常 false-连接异常
     */
    boolean isConnectionHealthy();

    /**
     * 获取今日消息数（用于数据看板）
     *
     * @return 今日消息数
     */
    Long getTodayMessageCount();

    /**
     * 获取消息趋势（近N天，用于数据看板）
     *
     * @param days 天数
     * @return Map<日期(MM-dd), 消息数>
     */
    Map<String, Long> getMessagesTrend(int days);

    /**
     * 获取消息统计信息
     * 包含：总消息数、按状态统计、按格式统计、撤回消息数
     *
     * @return 统计信息
     */
    Map<String, Object> getMessageStatistics();

    /**
     * 获取用户消息统计
     * 包含：发送消息数、接收消息数、参与的会话数
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    Map<String, Object> getUserMessageStatistics(String userId);

    /**
     * 获取会话消息统计
     * 包含：会话消息总数、最早消息时间、最新消息时间
     *
     * @param chatId 会话ID
     * @return 统计信息
     */
    Map<String, Object> getChatMessageStatistics(String chatId);
}
