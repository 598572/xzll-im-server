package com.xzll.console.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.UserBanDO;

/**
 * 用户封禁服务
 */
public interface BanService {
    IPage<UserBanDO> getPage(Page<UserBanDO> page, String userId);
    void banUser(String userId, String banReason, Long banDays, String adminId);
    void unbanUser(Long id, String unbanReason, String adminId);
}
