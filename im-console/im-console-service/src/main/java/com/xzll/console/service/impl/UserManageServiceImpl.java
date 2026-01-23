package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.console.dto.UserQueryDTO;
import com.xzll.console.entity.ImUserDO;
import com.xzll.console.mapper.ImFriendRelationMapper;
import com.xzll.console.mapper.ImUserMapper;
import com.xzll.console.service.UserManageService;
import com.xzll.console.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户管理服务实现
 */
@Slf4j
@Service
public class UserManageServiceImpl implements UserManageService {
    
    private static final String USER_ONLINE_KEY_PREFIX = "im:user:online:";
    
    @Resource
    private ImUserMapper imUserMapper;
    
    @Resource
    private ImFriendRelationMapper friendRelationMapper;
    
    @Resource
    private RedissonUtils redissonUtils;
    
    @Override
    public Page<UserVO> pageUsers(UserQueryDTO queryDTO) {
        Page<ImUserDO> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        
        LambdaQueryWrapper<ImUserDO> wrapper = new LambdaQueryWrapper<>();
        
        // 关键词搜索
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.and(w -> w
                    .like(ImUserDO::getUserId, queryDTO.getKeyword())
                    .or().like(ImUserDO::getUserName, queryDTO.getKeyword())
                    .or().like(ImUserDO::getUserFullName, queryDTO.getKeyword())
                    .or().like(ImUserDO::getPhone, queryDTO.getKeyword())
            );
        }
        
        // 精确查询
        if (StringUtils.hasText(queryDTO.getUserId())) {
            wrapper.eq(ImUserDO::getUserId, queryDTO.getUserId());
        }
        if (queryDTO.getSex() != null) {
            wrapper.eq(ImUserDO::getSex, queryDTO.getSex());
        }
        if (queryDTO.getTerminalType() != null) {
            wrapper.eq(ImUserDO::getRegisterTerminalType, queryDTO.getTerminalType());
        }
        
        wrapper.orderByDesc(ImUserDO::getCreateTime);
        
        Page<ImUserDO> result = imUserMapper.selectPage(page, wrapper);
        
        // 转换为VO
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<UserVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        
        return voPage;
    }
    
    @Override
    public UserVO getUserDetail(String userId) {
        LambdaQueryWrapper<ImUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImUserDO::getUserId, userId);
        ImUserDO user = imUserMapper.selectOne(wrapper);
        
        if (user == null) {
            return null;
        }
        
        UserVO vo = convertToVO(user);
        vo.setOnline(isUserOnline(userId));
        vo.setFriendCount(getFriendCount(userId));
        
        return vo;
    }
    
    @Override
    public List<UserVO> searchUsers(String keyword) {
        List<ImUserDO> users = imUserMapper.searchUsers(keyword);
        return users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean disableUser(String userId, String reason) {
        log.info("禁用用户: userId={}, reason={}", userId, reason);
        // TODO: 实现用户禁用逻辑（需要添加用户状态字段）
        // 1. 更新用户状态为禁用
        // 2. 踢用户下线
        // 3. 记录操作日志
        kickUser(userId);
        return true;
    }
    
    @Override
    public boolean enableUser(String userId) {
        log.info("启用用户: userId={}", userId);
        // TODO: 实现用户启用逻辑
        return true;
    }
    
    @Override
    public boolean kickUser(String userId) {
        log.info("踢用户下线: userId={}", userId);
        try {
            // 删除Redis中的在线状态
            String onlineKey = USER_ONLINE_KEY_PREFIX + userId;
            redissonUtils.delete(onlineKey);
            
            // TODO: 通过gRPC通知im-connect断开该用户连接
            
            return true;
        } catch (Exception e) {
            log.error("踢用户下线失败: userId={}", userId, e);
            return false;
        }
    }
    
    @Override
    public boolean isUserOnline(String userId) {
        try {
            String onlineKey = USER_ONLINE_KEY_PREFIX + userId;
            return redissonUtils.exists(onlineKey);
        } catch (Exception e) {
            log.error("检查用户在线状态失败: userId={}", userId, e);
            return false;
        }
    }
    
    @Override
    public Long getFriendCount(String userId) {
        return friendRelationMapper.countFriendsByUserId(userId);
    }
    
    /**
     * 将DO转换为VO
     */
    private UserVO convertToVO(ImUserDO user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        
        // 手机号脱敏
        if (StringUtils.hasText(user.getPhone()) && user.getPhone().length() >= 11) {
            vo.setPhone(user.getPhone().substring(0, 3) + "****" + user.getPhone().substring(7));
        }
        
        // 性别描述
        vo.setSexDesc(getSexDesc(user.getSex()));
        
        // 终端类型描述
        vo.setTerminalTypeDesc(getTerminalTypeDesc(user.getRegisterTerminalType()));
        
        return vo;
    }
    
    private String getSexDesc(Integer sex) {
        if (sex == null) return "未知";
        switch (sex) {
            case 0: return "女";
            case 1: return "男";
            default: return "未知";
        }
    }
    
    private String getTerminalTypeDesc(Integer terminalType) {
        if (terminalType == null) return "未知";
        switch (terminalType) {
            case 1: return "Android";
            case 2: return "iOS";
            case 3: return "小程序";
            case 4: return "Web";
            default: return "未知";
        }
    }
}
