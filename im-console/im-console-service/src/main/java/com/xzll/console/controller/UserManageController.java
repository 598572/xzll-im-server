package com.xzll.console.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.dto.UserQueryDTO;
import com.xzll.console.service.UserManageService;
import com.xzll.console.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/user")
@CrossOrigin(origins = "*")
public class UserManageController {
    
    @Resource
    private UserManageService userManageService;
    
    /**
     * 分页查询用户列表
     */
    @PostMapping("/page")
    public WebBaseResponse<Page<UserVO>> pageUsers(@RequestBody UserQueryDTO queryDTO) {
        try {
            Page<UserVO> result = userManageService.pageUsers(queryDTO);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询用户列表失败", e);
            return WebBaseResponse.returnResultError("查询用户列表失败");
        }
    }
    
    /**
     * 获取用户详情
     */
    @GetMapping("/detail/{userId}")
    public WebBaseResponse<UserVO> getUserDetail(@PathVariable String userId) {
        try {
            UserVO user = userManageService.getUserDetail(userId);
            if (user == null) {
                return WebBaseResponse.returnResultError("用户不存在");
            }
            return WebBaseResponse.returnResultSuccess(user);
        } catch (Exception e) {
            log.error("获取用户详情失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("获取用户详情失败");
        }
    }
    
    /**
     * 搜索用户
     */
    @GetMapping("/search")
    public WebBaseResponse<List<UserVO>> searchUsers(@RequestParam String keyword) {
        try {
            List<UserVO> users = userManageService.searchUsers(keyword);
            return WebBaseResponse.returnResultSuccess(users);
        } catch (Exception e) {
            log.error("搜索用户失败: keyword={}", keyword, e);
            return WebBaseResponse.returnResultError("搜索用户失败");
        }
    }
    
    /**
     * 禁用用户
     */
    @PostMapping("/disable/{userId}")
    public WebBaseResponse<String> disableUser(
            @PathVariable String userId,
            @RequestParam(required = false) String reason) {
        try {
            boolean success = userManageService.disableUser(userId, reason);
            if (success) {
                return WebBaseResponse.returnResultSuccess("用户已禁用");
            } else {
                return WebBaseResponse.returnResultError("禁用用户失败");
            }
        } catch (Exception e) {
            log.error("禁用用户失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("禁用用户失败");
        }
    }
    
    /**
     * 启用用户
     */
    @PostMapping("/enable/{userId}")
    public WebBaseResponse<String> enableUser(@PathVariable String userId) {
        try {
            boolean success = userManageService.enableUser(userId);
            if (success) {
                return WebBaseResponse.returnResultSuccess("用户已启用");
            } else {
                return WebBaseResponse.returnResultError("启用用户失败");
            }
        } catch (Exception e) {
            log.error("启用用户失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("启用用户失败");
        }
    }
    
    /**
     * 踢用户下线
     */
    @PostMapping("/kick/{userId}")
    public WebBaseResponse<String> kickUser(@PathVariable String userId) {
        try {
            boolean success = userManageService.kickUser(userId);
            if (success) {
                return WebBaseResponse.returnResultSuccess("用户已被踢下线");
            } else {
                return WebBaseResponse.returnResultError("踢用户下线失败");
            }
        } catch (Exception e) {
            log.error("踢用户下线失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("踢用户下线失败");
        }
    }
    
    /**
     * 检查用户是否在线
     */
    @GetMapping("/online/{userId}")
    public WebBaseResponse<Boolean> isUserOnline(@PathVariable String userId) {
        try {
            boolean online = userManageService.isUserOnline(userId);
            return WebBaseResponse.returnResultSuccess(online);
        } catch (Exception e) {
            log.error("检查用户在线状态失败: userId={}", userId, e);
            return WebBaseResponse.returnResultError("检查用户在线状态失败");
        }
    }
}
