package com.xzll.business.service;

import com.xzll.business.dto.request.ChatHistoryQueryDTO;
import com.xzll.business.dto.response.ChatHistoryResponseDTO;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: C2C消息记录存储服务接口
 *
 * 功能说明：
 * - 定义消息保存、状态更新、查询等操作
 * - 支持MongoDB存储实现
 */
public interface ImC2CMsgRecordService {

    /**
     * 保存C2C消息
     *
     * @param dto 消息数据
     * @return 保存是否成功
     */
    boolean saveC2CMsg(C2CSendMsgAO dto);

    /**
     * 更新消息离线状态
     *
     * @param dto 离线消息数据
     * @return 更新是否成功
     */
    boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto);

    /**
     * 更新消息接收状态
     *
     * @param dto 接收确认消息数据
     * @return 更新是否成功
     */
    boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto);

    /**
     * 更新消息撤回状态
     *
     * @param dto 撤回消息数据
     * @return 更新是否成功
     */
    boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto);

    /**
     * 根据消息ID查询消息记录
     *
     * @param chatId 会话ID（分片键）
     * @param msgId 消息ID
     * @return 消息记录
     */
    ImC2CMsgRecord getMessageByMsgId(String chatId, String msgId);

    /**
     * 批量查询最后一条消息记录
     *
     * @param chatMsgIds 会话ID和消息ID的映射
     * @return 会话ID到最后一条消息的映射
     */
    Map<String, ImC2CMsgRecord> batchGetLastMessages(Map<String, String> chatMsgIds);

    /**
     * 批量查询每个会话的最后一条消息记录
     *
     * @param chatIds 会话ID列表
     * @return 会话ID到最后一条消息的映射
     */
    Map<String, ImC2CMsgRecord> batchGetLastMessagesByChatIds(List<String> chatIds);

    /**
     * 批量查询消息记录
     *
     * @param rowKeys 文档ID列表（格式：chatId_msgId）
     * @return 文档ID到消息记录的映射
     */
    Map<String, ImC2CMsgRecord> batchGetMessages(List<String> rowKeys);

    /**
     * 查询聊天历史记录
     *
     * @param queryDTO 查询条件
     * @return 聊天历史记录
     */
    ChatHistoryResponseDTO queryChatHistory(ChatHistoryQueryDTO queryDTO);

    // ==================== 新增：分页查询方法（支持ES实体格式） ====================

    /**
     * 根据会话ID查询消息（分页）
     *
     * @param chatId  会话ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByChatId(String chatId, Pageable pageable);

    /**
     * 根据发送者ID查询消息（分页）
     *
     * @param fromUserId 发送者ID
     * @param pageable   分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByFromUserId(String fromUserId, Pageable pageable);

    /**
     * 根据接收者ID查询消息（分页）
     *
     * @param toUserId 接收者ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByToUserId(String toUserId, Pageable pageable);

    /**
     * 根据消息内容查询（分页）
     *
     * @param content  消息内容
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByContent(String content, Pageable pageable);

    /**
     * 根据会话ID和内容查询（分页）
     *
     * @param chatId  会话ID
     * @param content 消息内容
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByChatIdAndContent(String chatId, String content, Pageable pageable);

    /**
     * 根据时间范围查询（分页）
     *
     * @param chatId    会话ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByTimeRange(String chatId, Long startTime, Long endTime, Pageable pageable);

    /**
     * 根据消息状态查询（分页）
     *
     * @param chatId    会话ID
     * @param msgStatus 消息状态
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryByMsgStatus(String chatId, Integer msgStatus, Pageable pageable);

    /**
     * 复合查询（分页）
     *
     * @param chatId     会话ID（可选）
     * @param fromUserId 发送者ID（可选）
     * @param toUserId   接收者ID（可选）
     * @param content    消息内容（可选）
     * @param msgStatus  消息状态（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param pageable   分页参数
     * @return 分页结果
     */
    Page<ImC2CMsgRecordES> queryComplex(String chatId, String fromUserId, String toUserId,
                                         String content, Integer msgStatus, Long startTime, Long endTime,
                                         Pageable pageable);
}
