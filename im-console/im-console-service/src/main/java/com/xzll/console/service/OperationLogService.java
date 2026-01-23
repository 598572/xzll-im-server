package com.xzll.console.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.OperationLogDO;

/**
 * 操作日志服务
 */
public interface OperationLogService {
    IPage<OperationLogDO> getPage(Page<OperationLogDO> page, String adminId, String operationType);
    void recordLog(String adminId, String adminName, String operationType, String targetType, String targetId, String desc, String ip, String params, String result);
}
