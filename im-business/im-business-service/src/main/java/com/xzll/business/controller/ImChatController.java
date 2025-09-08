package com.xzll.business.controller;

import com.xzll.business.service.ImChatService;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.LastChatListAO;
import com.xzll.common.pojo.response.LastChatListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 会话管理控制器
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
@Slf4j
public class ImChatController {

    @Resource
    private ImChatService imChatService;

    /**
     * 查询最近会话列表
     * 此接口在用户登录后调用，获取初始会话列表
     * 后续会话列表更新完全依靠消息推送
     */
    @PostMapping("/lastChatList")
    public WebBaseResponse<List<LastChatListVO>> findLastChatList(@RequestBody LastChatListAO ao) {
        log.info("查询最近会话列表_入参:{}", ao);
        
        try {
            // 参数校验
            if (ao == null || ao.getUserId() == null) {
                WebBaseResponse<List<LastChatListVO>> failResponse = WebBaseResponse.returnResultError("用户ID不能为空");
                return WebBaseResponse.returnResultError(HttpStatus.BAD_REQUEST.toString());
            }
            
            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(20);
            }
            
            // 限制每页最大数量
            if (ao.getPageSize() > 100) {
                ao.setPageSize(100);
            }
            
            List<LastChatListVO> result = imChatService.findLastChatList(ao);
            
            log.info("查询最近会话列表成功，用户ID:{}, 返回{}条记录", ao.getUserId(), result.size());
            WebBaseResponse<List<LastChatListVO>> successResponse = WebBaseResponse.returnResultSuccess(result);
            return WebBaseResponse.returnResultError(HttpStatus.OK.toString());
            
        } catch (Exception e) {
            log.error("查询最近会话列表失败，用户ID:{}", ao != null ? ao.getUserId() : "null", e);
            WebBaseResponse<List<LastChatListVO>> errorResponse = WebBaseResponse.returnResultError("查询会话列表失败: " + e.getMessage());
            return WebBaseResponse.returnResultError(HttpStatus.INTERNAL_SERVER_ERROR.toString());
        }
    }
} 