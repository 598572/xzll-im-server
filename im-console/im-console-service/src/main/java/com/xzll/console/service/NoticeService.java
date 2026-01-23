package com.xzll.console.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.SystemNoticeDO;

/**
 * 系统公告服务
 */
public interface NoticeService {
    IPage<SystemNoticeDO> getPage(Page<SystemNoticeDO> page, Integer status);
    void save(SystemNoticeDO notice);
    void publish(Long id, String adminId);
    void revoke(Long id);
}
