package com.xzll.business.controller;

import com.xzll.business.service.UserProfileService;
import com.xzll.common.controller.BaseController;
import com.xzll.common.pojo.request.UpdateUserProfileAO;
import com.xzll.common.pojo.response.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 用户个人信息管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController extends BaseController {
    
    @Resource
    private UserProfileService userProfileService;
    
    /**
     * 获取当前用户个人信息（安全版本，只能查询自己的信息）
     */
    @GetMapping("/me")
    public UserProfileVO getUserProfile() {
        
        try {
            // 1. 从上下文获取当前登录用户ID（安全校验）
            String currentUserId = getCurrentUserIdWithValidation();
            if (currentUserId == null) {
                throw new RuntimeException("用户未登录或token无效");
            }
            
            log.info("获取用户个人信息，userId：{}", currentUserId);
            
            // 2. 通过service获取用户信息（根据userId字段查询）
            return userProfileService.getUserProfileByUserId(currentUserId);
            
        } catch (Exception e) {
            log.error("获取用户个人信息失败", e);
            throw new RuntimeException("获取用户信息失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新当前用户个人信息（安全版本，只能修改自己的信息）
     * 支持更新：用户名、全名、手机号、邮箱、性别、头像URL
     */
    @PutMapping("/update")
    public UserProfileVO updateUserProfile(@RequestBody UpdateUserProfileAO updateAO) {
        log.info("更新用户个人信息，参数：{}", updateAO);
        
        try {
            // 1. 从上下文获取当前登录用户ID（安全校验）
            String currentUserId = getCurrentUserIdWithValidation();
            if (currentUserId == null) {
                throw new RuntimeException("用户未登录或token无效");
            }
            
            // 2. 通过service更新用户信息（根据userId字段更新，包括头像）
            return userProfileService.updateUserProfile(currentUserId, updateAO);
            
        } catch (Exception e) {
            log.error("更新用户个人信息失败", e);
            throw new RuntimeException("更新用户信息失败：" + e.getMessage());
        }
    }

}
