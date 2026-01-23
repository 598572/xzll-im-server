package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.UserBanDO;
import com.xzll.console.mapper.UserBanMapper;
import com.xzll.console.service.BanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户封禁服务实现
 *
 * @author xzll
 */
@Slf4j
@Service
public class BanServiceImpl implements BanService {

    @Autowired
    private UserBanMapper userBanMapper;

    @Override
    public IPage<UserBanDO> getPage(Page<UserBanDO> page, String userId) {
        LambdaQueryWrapper<UserBanDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(userId)) {
            wrapper.eq(UserBanDO::getUserId, userId);
        }
        wrapper.orderByDesc(UserBanDO::getCreateTime);
        return userBanMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(String userId, String banReason, Long banDays, String adminId) {
        UserBanDO ban = new UserBanDO();
        ban.setUserId(userId);
        ban.setBanType(1); // 默认账号封禁
        ban.setBanReason(banReason);
        ban.setBanStartTime(java.time.LocalDateTime.now());
        if (banDays != null) {
            ban.setBanEndTime(java.time.LocalDateTime.now().plusDays(banDays));
        }
        ban.setBanBy(adminId);
        ban.setStatus(1); // 封禁中
        userBanMapper.insert(ban);
        log.info("用户封禁成功: userId={}, banReason={}, adminId={}", userId, banReason, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbanUser(Long id, String unbanReason, String adminId) {
        UserBanDO ban = userBanMapper.selectById(id);
        if (ban != null) {
            ban.setStatus(0); // 已解封
            ban.setUnbanTime(java.time.LocalDateTime.now());
            ban.setUnbanBy(adminId);
            ban.setUnbanReason(unbanReason);
            userBanMapper.updateById(ban);
            log.info("用户解封成功: id={}, unbanReason={}, adminId={}", id, unbanReason, adminId);
        } else {
            throw new RuntimeException("封禁记录不存在");
        }
    }
}
