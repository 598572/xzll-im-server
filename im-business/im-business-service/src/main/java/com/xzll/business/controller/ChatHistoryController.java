package com.xzll.business.controller;

import com.xzll.business.dto.request.ChatHistoryQueryDTO;
import com.xzll.business.dto.response.ChatHistoryResponseDTO;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.util.ChatIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2025/10/30
 * @Description: 聊天历史记录查询控制器 (基于HBase)
 *
 */
@RestController
@RequestMapping("/api/chat/c2c")
@CrossOrigin
@Slf4j
public class ChatHistoryController {

    @Autowired(required = false)
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;

    /**
     * 查询聊天历史记录
     * POST /im-business/api/chat/history
     * 
     * @param queryDTO 查询条件
     * @return 聊天历史记录响应
     */
    @PostMapping("/history")
    public WebBaseResponse<ChatHistoryResponseDTO> queryChatHistory(@Valid @RequestBody ChatHistoryQueryDTO queryDTO) {
        log.info("接收聊天历史查询请求: chatId={}, userId={}, lastMsgId={}, pageSize={}, startTime={}, endTime={}", 
                queryDTO.getChatId(), queryDTO.getUserId(), queryDTO.getLastMsgId(), 
                queryDTO.getPageSize(), queryDTO.getStartTime(), queryDTO.getEndTime());

        try {
            // 参数校验
            if (queryDTO.getChatId() == null || queryDTO.getChatId().trim().isEmpty()) {
                return WebBaseResponse.returnResultError("chatId不能为空");
            }
            
            // 应用层权限前置验证：检查用户是否有权访问该会话  TODO : 安全来说应该gateway带过来userId
            if (!isUserAuthorizedForChat(queryDTO.getUserId(), queryDTO.getChatId())) {
                return WebBaseResponse.returnResultError("无权访问此会话记录");
            }
            
            if (queryDTO.getPageSize() == null || queryDTO.getPageSize() <= 0) {
                queryDTO.setPageSize(50); // 默认50条
            }
            
            if (queryDTO.getPageSize() > 100) {
                queryDTO.setPageSize(100); // 最大100条
            }
            
            if (queryDTO.getReverse() == null) {
                queryDTO.setReverse(true); // 默认倒序
            }

            // 调用服务查询
            ChatHistoryResponseDTO response = imC2CMsgRecordHBaseService.queryChatHistory(queryDTO);
            
            log.info("聊天历史查询完成: chatId={}, userId={}, 查询到{}条记录, hasMore={}", 
                    queryDTO.getChatId(), queryDTO.getUserId(), 
                    response.getCurrentPageSize(), response.getHasMore());
            
            return WebBaseResponse.returnResultSuccess(response);
            
        } catch (Exception e) {
            log.error("聊天历史查询失败: chatId={}, userId={}", 
                    queryDTO.getChatId(), queryDTO.getUserId(), e);
            return WebBaseResponse.returnResultError("查询聊天历史失败: " + e.getMessage());
        }
    }

    /**
     * 检查用户是否有权访问指定会话
     * 使用ChatIdUtils统一解析chatId，验证当前用户是否为参与者之一
     * 
     * @param userId 当前用户ID
     * @param chatId 会话ID (格式如: 100-1-123-456)
     * @return 是否有权访问
     */
    private boolean isUserAuthorizedForChat(String userId, String chatId) {
        if (userId == null || chatId == null) {
            return false;
        }
        
        try {
            // 使用ChatIdUtils统一解析逻辑
            boolean authorized = ChatIdUtils.isUserAuthorizedForChat(userId, chatId);
            
            if (authorized) {
                log.debug("用户 {} 有权访问会话 {}", userId, chatId);
            } else {
                // 获取参与用户列表用于日志输出
                List<String> participants = ChatIdUtils.getParticipantUserIds(chatId);
                log.warn("用户 {} 无权访问会话 {}, 参与者: {}", userId, chatId, String.join(",", participants));
            }
            
            return authorized;
            
        } catch (Exception e) {
            log.error("验证会话权限失败: userId={}, chatId={}", userId, chatId, e);
            return false;
        }
    }

    /**
     * 从网关过来的请求头中获取当前登录用户ID
     *
     * @param request HTTP请求对象
     * @return 当前登录用户ID
     */
    private String getCurrentUserIdFromGateway(HttpServletRequest request) {
        //  TODO : 从GATEWAY的Header中获取
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }
        log.warn("无法从请求中获取用户ID，请检查网关配置");
        return null;
    }
}
