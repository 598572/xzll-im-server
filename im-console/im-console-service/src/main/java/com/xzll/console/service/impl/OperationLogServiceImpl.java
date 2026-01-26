package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.OperationLogDO;
import com.xzll.console.mapper.OperationLogMapper;
import com.xzll.console.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 操作日志服务实现
 *
 * @author xzll
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public IPage<OperationLogDO> getPage(Page<OperationLogDO> page, String adminId, String operationType) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(adminId)) {
            wrapper.eq(OperationLogDO::getAdminId, adminId);
        }
        if (StringUtils.hasText(operationType)) {
            wrapper.eq(OperationLogDO::getOperationType, operationType);
        }
        wrapper.orderByDesc(OperationLogDO::getCreateTime);
        return operationLogMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordLog(String adminId, String adminName, String operationType, String targetType,
                          String targetId, String desc, String ip, String params, String result) {
        OperationLogDO operationLog = new OperationLogDO();
        operationLog.setAdminId(adminId);
        operationLog.setAdminName(adminName);
        operationLog.setOperationType(operationType);
        operationLog.setTargetType(targetType);
        operationLog.setTargetId(targetId);
        operationLog.setOperationDesc(desc);
        operationLog.setRequestIp(ip);
        operationLog.setRequestParams(params);
        operationLog.setResponseResult(result);
        // 手动设置创建时间，避免 MyBatis-Plus 自动填充不生效的问题
        operationLog.setCreateTime(java.time.LocalDateTime.now());

        operationLogMapper.insert(operationLog);
    }
}
