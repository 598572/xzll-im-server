package com.xzll.business.service;

import com.xzll.business.dto.request.ChatHistoryQueryDTO;
import com.xzll.business.dto.response.ChatHistoryResponseDTO;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;

import java.util.List;
import java.util.Map;

public interface ImC2CMsgRecordHBaseService {


    public boolean saveC2CMsg(C2CSendMsgAO dto);

    public boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto);

    public boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto);

    public boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto);

    /**
     * 根据消息ID查询单条消息记录
     * @param chatId 会话ID
     * @param msgId 消息ID
     * @return 消息记录
     */
    ImC2CMsgRecord getMessageByMsgId(String chatId, String msgId);

    /**
     * 批量查询最后一条消息记录
     * @param chatMsgIds 会话ID和消息ID的映射 Map<chatId, msgId>
     * @return 消息记录映射 Map<chatId, ImC2CMsgRecord>
     */
    Map<String, ImC2CMsgRecord> batchGetLastMessages(Map<String, String> chatMsgIds);

    /**
     * 批量查询每个会话的最后一条消息记录
     * @param chatIds 会话ID列表
     * @return 消息记录映射 Map<chatId, ImC2CMsgRecord>
     */
    Map<String, ImC2CMsgRecord> batchGetLastMessagesByChatIds(List<String> chatIds);

    /**
     * 查询聊天历史记录 (基于HBase范围扫描)
     * @param queryDTO 查询条件
     * @return 聊天历史记录响应
     */
    ChatHistoryResponseDTO queryChatHistory(ChatHistoryQueryDTO queryDTO);
}
