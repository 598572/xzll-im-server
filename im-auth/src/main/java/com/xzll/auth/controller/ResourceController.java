package com.xzll.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xzll.auth.entity.ImResourceRole;
import com.xzll.auth.mapper.ImResourceRoleMapper;
import com.xzll.auth.service.ResourceService;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 资源权限管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/resource")
public class ResourceController {

    @Resource
    private ResourceService resourceService;
    
    @Resource
    private ImResourceRoleMapper resourceRoleMapper;

    /**
     * 查询所有资源权限配置
     */
    @GetMapping("/list")
    public WebBaseResponse<List<ImResourceRole>> listResources() {
        try {
            List<ImResourceRole> resources = resourceRoleMapper.selectList(null);
            return WebBaseResponse.returnResultSuccess(resources);
        } catch (Exception e) {
            log.error("查询资源权限配置失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询资源权限配置
     */
    @GetMapping("/{id}")
    public WebBaseResponse<ImResourceRole> getResource(@PathVariable Long id) {
        try {
            ImResourceRole resource = resourceRoleMapper.selectById(id);
            if (resource == null) {
                return WebBaseResponse.returnResultError("资源权限配置不存在");
            }
            return WebBaseResponse.returnResultSuccess(resource);
        } catch (Exception e) {
            log.error("查询资源权限配置失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 新增资源权限配置
     */
    @PostMapping("/add")
    public WebBaseResponse<Boolean> addResource(@RequestBody ImResourceRole resource) {
        try {
            // 检查资源路径是否已存在
            LambdaQueryWrapper<ImResourceRole> queryWrapper = Wrappers.lambdaQuery(ImResourceRole.class)
                    .eq(ImResourceRole::getResourcePath, resource.getResourcePath());
            ImResourceRole existing = resourceRoleMapper.selectOne(queryWrapper);
            
            if (existing != null) {
                return WebBaseResponse.returnResultError("资源路径已存在");
            }
            
            resource.setCreateTime(LocalDateTime.now());
            resource.setUpdateTime(LocalDateTime.now());
            if (resource.getStatus() == null) {
                resource.setStatus(1); // 默认启用
            }
            
            int result = resourceRoleMapper.insert(resource);
            if (result > 0) {
                // 刷新配置
                resourceService.refreshConfig();
                return WebBaseResponse.returnResultSuccess(true);
            } else {
                return WebBaseResponse.returnResultError("新增失败");
            }
        } catch (Exception e) {
            log.error("新增资源权限配置失败", e);
            return WebBaseResponse.returnResultError("新增失败: " + e.getMessage());
        }
    }

    /**
     * 更新资源权限配置
     */
    @PostMapping("/update")
    public WebBaseResponse<Boolean> updateResource(@RequestBody ImResourceRole resource) {
        try {
            if (resource.getId() == null) {
                return WebBaseResponse.returnResultError("ID不能为空");
            }
            
            ImResourceRole existing = resourceRoleMapper.selectById(resource.getId());
            if (existing == null) {
                return WebBaseResponse.returnResultError("资源权限配置不存在");
            }
            
            resource.setUpdateTime(LocalDateTime.now());
            int result = resourceRoleMapper.updateById(resource);
            
            if (result > 0) {
                // 刷新配置
                resourceService.refreshConfig();
                return WebBaseResponse.returnResultSuccess(true);
            } else {
                return WebBaseResponse.returnResultError("更新失败");
            }
        } catch (Exception e) {
            log.error("更新资源权限配置失败", e);
            return WebBaseResponse.returnResultError("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除资源权限配置
     */
    @PostMapping("/delete/{id}")
    public WebBaseResponse<Boolean> deleteResource(@PathVariable Long id) {
        try {
            ImResourceRole existing = resourceRoleMapper.selectById(id);
            if (existing == null) {
                return WebBaseResponse.returnResultError("资源权限配置不存在");
            }
            
            int result = resourceRoleMapper.deleteById(id);
            if (result > 0) {
                // 刷新配置
                resourceService.refreshConfig();
                return WebBaseResponse.returnResultSuccess(true);
            } else {
                return WebBaseResponse.returnResultError("删除失败");
            }
        } catch (Exception e) {
            log.error("删除资源权限配置失败", e);
            return WebBaseResponse.returnResultError("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用资源权限配置
     */
    @PostMapping("/toggle/{id}")
    public WebBaseResponse<Boolean> toggleResource(@PathVariable Long id) {
        try {
            ImResourceRole existing = resourceRoleMapper.selectById(id);
            if (existing == null) {
                return WebBaseResponse.returnResultError("资源权限配置不存在");
            }
            
            // 切换状态
            existing.setStatus(existing.getStatus() == 1 ? 0 : 1);
            existing.setUpdateTime(LocalDateTime.now());
            
            int result = resourceRoleMapper.updateById(existing);
            if (result > 0) {
                // 刷新配置
                resourceService.refreshConfig();
                return WebBaseResponse.returnResultSuccess(true);
            } else {
                return WebBaseResponse.returnResultError("状态更新失败");
            }
        } catch (Exception e) {
            log.error("切换资源权限配置状态失败", e);
            return WebBaseResponse.returnResultError("操作失败: " + e.getMessage());
        }
    }

    /**
     * 刷新配置（重新从数据库加载到内存和Redis）
     */
    @PostMapping("/refresh")
    public WebBaseResponse<Boolean> refreshConfig() {
        try {
            resourceService.refreshConfig();
            return WebBaseResponse.returnResultSuccess(true);
        } catch (Exception e) {
            log.error("刷新配置失败", e);
            return WebBaseResponse.returnResultError("刷新失败: " + e.getMessage());
        }
    }

    /**
     * 查看当前内存中的配置
     */
    @GetMapping("/current")
    public WebBaseResponse<Map<String, List<String>>> getCurrentConfig() {
        try {
            Map<String, List<String>> config = resourceService.getAllResourceRoles();
            return WebBaseResponse.returnResultSuccess(config);
        } catch (Exception e) {
            log.error("查询当前配置失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }
}
