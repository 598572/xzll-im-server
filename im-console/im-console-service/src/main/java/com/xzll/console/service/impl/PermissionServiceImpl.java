package com.xzll.console.service.impl;

import cn.hutool.core.convert.Convert;
import com.xzll.common.constant.RedisConstant;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.console.service.PermissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限管理服务实现类
 *
 * 功能：
 * 1. 动态管理接口权限配置
 * 2. 查询接口所需权限
 * 3. 权限配置的增删改查
 *
 * @Author: hzz
 * @Date: 2024/6/10 09:52:49
 */
@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private RedissonUtils redissonUtils;

    @Override
    public List<String> getRequiredPermissions(String path) {
        try {
            Object obj = redissonUtils.getHash(RedisConstant.RESOURCE_ROLES_MAP, path);
            if (obj == null) {
                log.debug("接口未配置权限: {}", path);
                return null;
            }
            
            List<String> permissions = Convert.toList(String.class, obj);
            log.debug("接口权限配置: {} -> {}", path, permissions);
            return permissions;
        } catch (Exception e) {
            log.error("获取接口权限配置失败: {}", path, e);
            return null;
        }
    }

    @Override
    public boolean setRequiredPermissions(String path, List<String> permissions) {
        try {
            if (permissions == null || permissions.isEmpty()) {
                log.warn("权限列表为空，移除接口权限配置: {}", path);
                redissonUtils.deleteHash(RedisConstant.RESOURCE_ROLES_MAP, path);
                return true;
            }
            
            String permissionsStr = String.join(",", permissions);
            redissonUtils.setHash(RedisConstant.RESOURCE_ROLES_MAP, path, permissionsStr);
            log.info("设置接口权限配置成功: {} -> {}", path, permissions);
            return true;
        } catch (Exception e) {
            log.error("设置接口权限配置失败: {} -> {}", path, permissions, e);
            return false;
        }
    }

    @Override
    public boolean removeRequiredPermissions(String path) {
        try {
            redissonUtils.deleteHash(RedisConstant.RESOURCE_ROLES_MAP, path);
            log.info("移除接口权限配置成功: {}", path);
            return true;
        } catch (Exception e) {
            log.error("移除接口权限配置失败: {}", path, e);
            return false;
        }
    }

    @Override
    public Map<String, String> getAllPermissions() {
        try {
            Map<String, String> map = redissonUtils.getAllHashWithStringCodec(RedisConstant.RESOURCE_ROLES_MAP);
            if (map == null || map.isEmpty()) {
                log.debug("权限配置为空");
                return new HashMap<>();
            }

            log.info("获取所有权限配置成功，总数: {}", map.size());
            return map;
        } catch (Exception e) {
            log.error("获取所有权限配置失败", e);
            return new HashMap<>();
        }
    }

    @Override
    public boolean requiresPermission(String path) {
        List<String> permissions = getRequiredPermissions(path);
        return permissions != null && !permissions.isEmpty();
    }

    @Override
    public int batchSetPermissions(Map<String, List<String>> permissionsMap) {
        if (permissionsMap == null || permissionsMap.isEmpty()) {
            log.warn("批量设置权限配置：参数为空");
            return 0;
        }

        int successCount = 0;
        int totalCount = permissionsMap.size();

        for (Map.Entry<String, List<String>> entry : permissionsMap.entrySet()) {
            boolean success = setRequiredPermissions(entry.getKey(), entry.getValue());
            if (success) {
                successCount++;
            }
        }

        log.info("批量设置权限配置完成，成功: {}/{}", successCount, totalCount);
        return successCount;
    }

    @Override
    public boolean clearAllPermissions() {
        try {
            redissonUtils.delete(RedisConstant.RESOURCE_ROLES_MAP);
            log.info("清空所有权限配置成功");
            return true;
        } catch (Exception e) {
            log.error("清空所有权限配置失败", e);
            return false;
        }
    }

    @Override
    public int getPermissionCount() {
        try {
            Map<String, String> map = redissonUtils.getAllHashWithStringCodec(RedisConstant.RESOURCE_ROLES_MAP);
            int count = map != null ? map.size() : 0;
            log.debug("获取权限配置数量: {}", count);
            return count;
        } catch (Exception e) {
            log.error("获取权限配置数量失败", e);
            return 0;
        }
    }
}
