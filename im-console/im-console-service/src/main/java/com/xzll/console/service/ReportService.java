package com.xzll.console.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.ReportDO;

/**
 * 举报处理服务
 */
public interface ReportService {
    IPage<ReportDO> getPage(Page<ReportDO> page, Integer status);
    void handleReport(Long id, Integer handleResult, String result, String adminId);
}
