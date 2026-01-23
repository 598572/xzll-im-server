package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.SystemNoticeDO;
import com.xzll.console.mapper.SystemNoticeMapper;
import com.xzll.console.service.NoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统公告服务实现
 *
 * @author xzll
 */
@Slf4j
@Service
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private SystemNoticeMapper systemNoticeMapper;

    @Override
    public IPage<SystemNoticeDO> getPage(Page<SystemNoticeDO> page, Integer status) {
        LambdaQueryWrapper<SystemNoticeDO> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SystemNoticeDO::getStatus, status);
        }
        wrapper.orderByDesc(SystemNoticeDO::getCreateTime);
        return systemNoticeMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SystemNoticeDO notice) {
        // 默认状态为草稿
        if (notice.getStatus() == null) {
            notice.setStatus(0);
        }
        systemNoticeMapper.insert(notice);
        log.info("公告创建成功: title={}, createBy={}", notice.getTitle(), notice.getCreateBy());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, String adminId) {
        SystemNoticeDO notice = systemNoticeMapper.selectById(id);
        if (notice == null) {
            throw new RuntimeException("公告不存在");
        }

        notice.setStatus(1); // 已发布
        notice.setPublishTime(java.time.LocalDateTime.now());
        systemNoticeMapper.updateById(notice);
        log.info("公告发布成功: id={}, title={}, adminId={}", id, notice.getTitle(), adminId);

        // TODO: 这里可以添加推送逻辑，将公告推送给目标用户
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        SystemNoticeDO notice = systemNoticeMapper.selectById(id);
        if (notice == null) {
            throw new RuntimeException("公告不存在");
        }

        notice.setStatus(2); // 已撤回
        systemNoticeMapper.updateById(notice);
        log.info("公告撤回成功: id={}, title={}", id, notice.getTitle());
    }
}
