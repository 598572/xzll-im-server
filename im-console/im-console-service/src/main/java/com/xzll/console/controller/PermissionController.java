package com.xzll.console.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:52:49
 * @Description: 权限管理控制器
 * <p>
 * 功能：
 * 1. 提供权限配置的REST API接口
 * 2. 支持动态管理接口权限
 * 3. 查询权限配置信息
 */
@Slf4j
@RestController
@RequestMapping("/api/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    /**
     * 获取接口所需权限
     *
     * @param path 接口路径
     * @return 权限列表
     */
    @GetMapping("/required")
    public WebBaseResponse<List<String>> getRequiredPermissions(@RequestParam String path) {
        try {
            List<String> permissions = permissionService.getRequiredPermissions(path);
            if (permissions == null) {
                return WebBaseResponse.returnResultSuccess("接口未配置权限");
            }
            return WebBaseResponse.returnResultSuccess(permissions);
        } catch (Exception e) {
            log.error("获取接口权限失败: {}", path, e);
            return WebBaseResponse.returnResultError("获取接口权限失败");
        }
    }

    /**
     * 设置接口权限配置
     *
     * @param path 接口路径
     * @param permissions 权限列表
     * @return 设置结果
     */
    @PostMapping("/set")
    public WebBaseResponse<String> setRequiredPermissions(
            @RequestParam String path,
            @RequestBody List<String> permissions) {
        try {
            boolean success = permissionService.setRequiredPermissions(path, permissions);
            if (success) {
                return WebBaseResponse.returnResultSuccess("权限配置设置成功");
            } else {
                return WebBaseResponse.returnResultError("权限配置设置失败");
            }
        } catch (Exception e) {
            log.error("设置接口权限失败: {} -> {}", path, permissions, e);
            return WebBaseResponse.returnResultError("设置接口权限失败");
        }
    }

    /**
     * 移除接口权限配置
     *
     * @param path 接口路径
     * @return 移除结果
     */
    @DeleteMapping("/remove")
    public WebBaseResponse<String> removeRequiredPermissions(@RequestParam String path) {
        try {
            boolean success = permissionService.removeRequiredPermissions(path);
            if (success) {
                return WebBaseResponse.returnResultSuccess("权限配置移除成功");
            } else {
                return WebBaseResponse.returnResultError("权限配置移除失败");
            }
        } catch (Exception e) {
            log.error("移除接口权限失败: {}", path, e);
            return WebBaseResponse.returnResultError("移除接口权限失败");
        }
    }

    /**
     * 检查接口是否需要权限验证
     *
     * @param path 接口路径
     * @return 检查结果
     */
    @GetMapping("/check")
    public WebBaseResponse<Boolean> requiresPermission(@RequestParam String path) {
        try {
            boolean requires = permissionService.requiresPermission(path);
            return WebBaseResponse.returnResultSuccess(requires);
        } catch (Exception e) {
            log.error("检查接口权限失败: {}", path, e);
            return WebBaseResponse.returnResultError("检查接口权限失败");
        }
    }

    /**
     * 批量设置接口权限配置
     *
     * @param permissionsMap 权限配置映射
     * @return 设置结果
     */
    @PostMapping("/batch-set")
    public WebBaseResponse<String> batchSetPermissions(@RequestBody Map<String, List<String>> permissionsMap) {
        try {
            int successCount = 0;
            int totalCount = permissionsMap.size();
            
            for (Map.Entry<String, List<String>> entry : permissionsMap.entrySet()) {
                boolean success = permissionService.setRequiredPermissions(entry.getKey(), entry.getValue());
                if (success) {
                    successCount++;
                }
            }
            
            String message = String.format("批量设置完成，成功: %d/%d", successCount, totalCount);
            return WebBaseResponse.returnResultSuccess(message);
        } catch (Exception e) {
            log.error("批量设置接口权限失败", e);
            return WebBaseResponse.returnResultError("批量设置接口权限失败");
        }
    }
} 