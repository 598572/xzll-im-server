package com.xzll.business.controller;

import com.xzll.business.service.ImFriendService;
import com.xzll.common.controller.BaseController;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.*;
import com.xzll.common.pojo.response.FriendInfoVO;
import com.xzll.common.pojo.response.FriendRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友管理控制器
 */
@RestController
@RequestMapping("/api/friend")
@CrossOrigin
@Slf4j
public class ImFriendController extends BaseController {

    @Resource
    private ImFriendService imFriendService;

    /**
     * 发送好友申请
     */
    @PostMapping("/request/send")
    public WebBaseResponse<String> sendFriendRequest(@RequestBody FriendRequestSendAO ao) {
        log.info("发送好友申请_入参:{}", ao);
        
        try {
            String fromUserId = getCurrentUserIdWithValidation();
            if (fromUserId == null) {
                return  WebBaseResponse.returnResultError("用户未登录或token无效");
            }
            ao.setFromUserId(fromUserId);
            
            String requestId = imFriendService.sendFriendRequest(ao);
            log.info("发送好友申请成功，申请ID:{}", requestId);
            return WebBaseResponse.returnResultSuccess(requestId);
            
        } catch (IllegalArgumentException e) {
            log.warn("发送好友申请失败，参数错误:{}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
            
        } catch (Exception e) {
            log.error("发送好友申请失败", e);
            return WebBaseResponse.returnResultError("发送好友申请失败: " + e.getMessage());
        }
    }

    /**
     * 处理好友申请
     */
    @PostMapping("/request/handle")
    public WebBaseResponse<Boolean> handleFriendRequest(@RequestBody FriendRequestHandleAO ao) {
        log.info("处理好友申请_入参:{}", ao);
        
        String userId = getCurrentUserIdWithValidation();
        if (userId == null) {
            return  WebBaseResponse.returnResultError("用户未登录或token无效");
        }
        ao.setUserId(userId);

        try {
            boolean result = imFriendService.handleFriendRequest(ao);
            log.info("处理好友申请成功，申请ID:{}, 处理结果:{}", ao.getRequestId(), ao.getHandleResult());
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("处理好友申请失败，参数错误:{}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
            
        } catch (Exception e) {
            log.error("处理好友申请失败", e);
            return WebBaseResponse.returnResultError("处理好友申请失败: " + e.getMessage());
        }
    }

    /**
     * 查询好友申请列表
     */
    @PostMapping("/request/list")
    public WebBaseResponse<List<FriendRequestVO>> findFriendRequestList(@RequestBody FriendRequestListAO ao) {
        log.info("查询好友申请列表_入参:{}", ao);
        
        // 从请求头获取当前用户ID
        String userId = getCurrentUserIdWithValidation();
        if (userId == null) {
            return WebBaseResponse.returnResultError("用户未登录或token无效");
        }

        ao.setUserId(userId);

        try {
            // 参数校验和设置默认值
            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(20);
            }
            if (ao.getPageSize() > 100) {
                ao.setPageSize(100);
            }
            if (ao.getRequestType() == null) {
                ao.setRequestType(2); // 默认查询收到的申请
            }
            
            List<FriendRequestVO> result = imFriendService.findFriendRequestList(ao);
            log.info("查询好友申请列表成功，用户ID:{}, 返回{}条记录", ao.getUserId(), result.size());
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("查询好友申请列表失败，参数错误:{}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
            
        } catch (Exception e) {
            log.error("查询好友申请列表失败，用户ID:{}", ao != null ? ao.getUserId() : "null", e);
            return WebBaseResponse.returnResultError("查询好友申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询好友列表
     */
    @PostMapping("/list")
    public WebBaseResponse<List<FriendInfoVO>> findFriendList(@RequestBody FriendListAO ao) {
        log.info("查询好友列表_入参:{}", ao);
        
        // 从请求头获取当前用户ID
        String userId = getCurrentUserIdWithValidation();
        if (userId == null) {
            return WebBaseResponse.returnResultError("用户未登录或token无效");
        }
        
        // 设置用户ID
        ao.setUserId(userId);

        try {
            // 参数校验和设置默认值
            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(20);
            }
            if (ao.getPageSize() > 100) {
                ao.setPageSize(100);
            }
            
            List<FriendInfoVO> result = imFriendService.findFriendList(ao);
            log.info("查询好友列表成功，用户ID:{}, 返回{}条记录", ao.getUserId(), result.size());
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("查询好友列表失败，参数错误:{}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
            
        } catch (Exception e) {
            log.error("查询好友列表失败，用户ID:{}", ao != null ? ao.getUserId() : "null", e);
            return WebBaseResponse.returnResultError("查询好友列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除好友
     */
    @PostMapping("/delete")
    public WebBaseResponse<Boolean> deleteFriend(@RequestParam String friendId) {
        String userId = getCurrentUserIdWithValidation();
        if (userId == null) {
            return WebBaseResponse.returnResultError("用户未登录或token无效");
        }

        log.info("删除好友，用户ID:{}, 好友ID:{}", userId, friendId);


        try {
            // 参数校验
            if (userId == null || userId.trim().isEmpty()) {
                return WebBaseResponse.returnResultError("用户ID不能为空");
            }
            if (friendId == null || friendId.trim().isEmpty()) {
                return WebBaseResponse.returnResultError("好友ID不能为空");
            }
            if (userId.equals(friendId)) {
                return WebBaseResponse.returnResultError("不能删除自己");
            }
            
            boolean result = imFriendService.deleteFriend(userId, friendId);
            log.info("删除好友成功，用户ID:{}, 好友ID:{}", userId, friendId);
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("删除好友失败，参数错误:{}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
            
        } catch (Exception e) {
            log.error("删除好友失败，用户ID:{}, 好友ID:{}", userId, friendId, e);
            return WebBaseResponse.returnResultError("删除好友失败: " + e.getMessage());
        }
    }

    /**
     * 拉黑/取消拉黑好友
     */
    @PostMapping("/block")
    public WebBaseResponse<Boolean> blockFriend(@RequestParam String friendId, 
                                                @RequestParam Boolean blackFlag) {
        String userId = getCurrentUserIdWithValidation();
        if (userId == null) {
            return WebBaseResponse.returnResultError("用户未登录或token无效");
        }
        
        log.info("拉黑好友请求 - 用户ID: {}, 好友ID: {}, 拉黑标志: {}", userId, friendId, blackFlag);


        try {
            // 参数校验
            if (userId == null || userId.trim().isEmpty()) {
                return WebBaseResponse.returnResultError("用户ID不能为空");
            }
            if (friendId == null || friendId.trim().isEmpty()) {
                return WebBaseResponse.returnResultError("好友ID不能为空");
            }
            if (userId.equals(friendId)) {
                return WebBaseResponse.returnResultError("不能对自己进行此操作");
            }
            if (blackFlag == null) {
                return WebBaseResponse.returnResultError("拉黑标志不能为空");
            }
            
            boolean result = imFriendService.blockFriend(userId, friendId, blackFlag);
            log.info("{}好友成功，用户ID:{}, 好友ID:{}", blackFlag ? "拉黑" : "取消拉黑", userId, friendId);
            return WebBaseResponse.returnResultSuccess(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("{}好友失败，参数错误:{}", blackFlag ? "拉黑" : "取消拉黑", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
            
        } catch (Exception e) {
            log.error("{}好友失败，用户ID:{}, 好友ID:{}", blackFlag ? "拉黑" : "取消拉黑", userId, friendId, e);
            return WebBaseResponse.returnResultError((blackFlag ? "拉黑" : "取消拉黑") + "好友失败: " + e.getMessage());
        }
    }
}
