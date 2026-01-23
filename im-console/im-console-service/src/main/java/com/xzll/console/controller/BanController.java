package com.xzll.console.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.UserBanDO;
import com.xzll.console.service.BanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户封禁Controller
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ban")
@CrossOrigin(origins = "*")
public class BanController {

    @Autowired
    private BanService banService;

    /**
     * 分页查询封禁列表
     */
    @GetMapping("/page")
    public WebBaseResponse<IPage<UserBanDO>> page(@RequestParam(defaultValue = "1") Integer current,
                                         @RequestParam(defaultValue = "10") Integer size,
                                         @RequestParam(required = false) String userId) {
        try {
            Page<UserBanDO> page = new Page<>(current, size);
            IPage<UserBanDO> result = banService.getPage(page, userId);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询封禁列表失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 封禁用户
     */
    @PostMapping("/user/{userId}")
    public WebBaseResponse<Void> banUser(@PathVariable String userId,
                                 @RequestParam String banReason,
                                 @RequestParam(required = false) Long banDays,
                                 @RequestHeader("X-Admin-Id") String adminId) {
        try {
            banService.banUser(userId, banReason, banDays, adminId);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("封禁用户失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 解封用户
     */
    @PostMapping("/unban/{id}")
    public WebBaseResponse<Void> unbanUser(@PathVariable Long id,
                                   @RequestParam(required = false) String unbanReason,
                                   @RequestHeader("X-Admin-Id") String adminId) {
        try {
            banService.unbanUser(id, unbanReason, adminId);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("解封用户失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }
}
