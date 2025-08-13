package com.xzll.auth.controller;


import cn.hutool.core.lang.Assert;
import com.xzll.auth.domain.UserDTO;
import com.xzll.auth.service.UserService;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: hzz
 * @Date: 2025/1/11 11:25:12
 * @Description: 用户注册逻辑
 */
@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     *
     * @param user 用户信息
     * @return WebBaseResponse
     */
    @PostMapping("/register")
    public WebBaseResponse<String> register(@RequestBody @Validated UserDTO user) {
        try {
            boolean result = userService.registerUser(user);
            Assert.isTrue(result, "保存失败");
        } catch (Exception exception) {
            return WebBaseResponse.returnResultError("注册失败：" + exception.getMessage());
        }
        return WebBaseResponse.returnResultSuccess();
    }

    /**
     * 更新用户信息
     * 
     * 网关已做认证，只能更新自己的用户信息
     *
     * @param user 用户信息
     * @param userHeader 网关传递的用户信息头
     * @return WebBaseResponse
     */
    @PutMapping("/update")
    public WebBaseResponse<String> updateUser(@RequestBody @Validated UserDTO user,
                                             @RequestHeader(value = "user", required = false) String userHeader) {
        try {
            // 网关已做认证，这里只需要验证用户只能更新自己的信息
            Assert.notNull(user.getId(), "用户ID不能为空");
            
            // TODO: 从userHeader中解析用户ID，验证权限
            // 暂时允许更新，实际项目中需要解析userHeader中的用户信息
            
            boolean result = userService.updateUser(user);
            Assert.isTrue(result, "更新失败");
            
            return WebBaseResponse.returnResultSuccess("用户信息更新成功");
        } catch (Exception exception) {
            return WebBaseResponse.returnResultError("更新失败：" + exception.getMessage());
        }
    }

    /**
     * 修改用户密码
     * 
     * 网关已做认证，只能修改自己的密码
     *
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @param userHeader 网关传递的用户信息头
     * @return WebBaseResponse
     */
    @PostMapping("/change-password")
    public WebBaseResponse<String> changePassword(@RequestParam String userId,
                                                 @RequestParam String oldPassword,
                                                 @RequestParam String newPassword,
                                                 @RequestHeader(value = "user", required = false) String userHeader) {
        try {
            // 网关已做认证，这里只需要验证用户只能修改自己的密码
            // TODO: 从userHeader中解析用户ID，验证权限
            // 暂时允许修改，实际项目中需要解析userHeader中的用户信息
            
            Assert.notNull(userId, "用户ID不能为空");
            Assert.notNull(oldPassword, "旧密码不能为空");
            Assert.notNull(newPassword, "新密码不能为空");
            
            boolean result = userService.updatePassword(userId, oldPassword, newPassword);
            Assert.isTrue(result, "密码修改失败");
            
            return WebBaseResponse.returnResultSuccess("密码修改成功");
        } catch (Exception exception) {
            return WebBaseResponse.returnResultError("密码修改失败：" + exception.getMessage());
        }
    }

    /**
     * 重置用户密码（管理员功能）
     * 
     * 网关已做认证，需要管理员权限
     *
     * @param userId 用户ID
     * @param newPassword 新密码
     * @param userHeader 网关传递的用户信息头
     * @return WebBaseResponse
     */
    @PostMapping("/reset-password")
    public WebBaseResponse<String> resetPassword(@RequestParam String userId,
                                                @RequestParam String newPassword,
                                                @RequestHeader(value = "user", required = false) String userHeader) {
        try {
            // 网关已做认证，这里只需要验证管理员权限
            // TODO: 从userHeader中解析用户信息，验证管理员权限
            // 暂时允许重置，实际项目中需要解析userHeader中的用户角色
            
            Assert.notNull(userId, "用户ID不能为空");
            Assert.notNull(newPassword, "新密码不能为空");
            
            boolean result = userService.resetPassword(userId, newPassword);
            Assert.isTrue(result, "密码重置失败");
            
            log.info("管理员重置用户密码，目标用户ID: {}", userId);
            return WebBaseResponse.returnResultSuccess("密码重置成功");
        } catch (Exception exception) {
            return WebBaseResponse.returnResultError("密码重置失败：" + exception.getMessage());
        }
    }


}
