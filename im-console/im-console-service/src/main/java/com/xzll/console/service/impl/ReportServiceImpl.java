package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.ReportDO;
import com.xzll.console.mapper.ReportMapper;
import com.xzll.console.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报处理服务实现
 *
 * @author xzll
 */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Override
    public IPage<ReportDO> getPage(Page<ReportDO> page, Integer status) {
        LambdaQueryWrapper<ReportDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ReportDO::getStatus, status);
        }
        wrapper.orderByDesc(ReportDO::getCreateTime);
        return reportMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long id, Integer handleResult, String result, String adminId) {
        ReportDO report = reportMapper.selectById(id);
        if (report == null) {
            throw new RuntimeException("举报记录不存在");
        }

        // 更新处理状态
        report.setStatus(handleResult); // 2-已处理，3-已驳回
        report.setHandleResult(result);
        report.setHandleBy(adminId);
        report.setHandleTime(java.time.LocalDateTime.now());

        reportMapper.updateById(report);
        log.info("举报处理完成: id={}, handleResult={}, adminId={}", id, handleResult, adminId);
    }
}
