package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.console.dto.UserQueryDTO;
import com.xzll.console.entity.ImUserDO;
import com.xzll.console.mapper.ImFriendRelationMapper;
import com.xzll.console.mapper.ImUserMapper;
import com.xzll.console.service.OperationLogService;
import com.xzll.console.service.UserManageService;
import com.xzll.console.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户管理服务实现
 */
@Slf4j
@Service
public class UserManageServiceImpl implements UserManageService {

    // 【修复】使用正确的用户登录状态 key（Hash 结构）
    private static final String USER_LOGIN_STATUS_KEY = "userLogin:status:";

    @Resource
    private ImUserMapper imUserMapper;

    @Resource
    private ImFriendRelationMapper friendRelationMapper;

    @Resource
    private RedissonUtils redissonUtils;

    @Resource
    private OperationLogService operationLogService;
    
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

        try {
            // 1. 查询用户是否存在
            LambdaQueryWrapper<ImUserDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImUserDO::getUserId, userId);
            ImUserDO user = imUserMapper.selectOne(wrapper);

            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return false;
            }

            // 2. 更新用户状态为禁用
            user.setStatus(1); // 1-禁用
            int updated = imUserMapper.updateById(user);

            if (updated > 0) {
                // 3. 踢用户下线
                kickUser(userId);

                // 4. 记录操作日志
                recordOperationLog("SYSTEM", "系统管理员", "USER_DISABLE",
                        "用户", userId, "禁用用户: " + user.getUserName() + ", 原因: " + reason);

                log.info("用户禁用成功: userId={}", userId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("禁用用户失败: userId={}", userId, e);
            return false;
        }
    }
    
    @Override
    public boolean enableUser(String userId) {
        log.info("启用用户: userId={}", userId);

        try {
            // 1. 查询用户是否存在
            LambdaQueryWrapper<ImUserDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImUserDO::getUserId, userId);
            ImUserDO user = imUserMapper.selectOne(wrapper);

            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return false;
            }

            // 2. 更新用户状态为正常
            user.setStatus(0); // 0-正常
            int updated = imUserMapper.updateById(user);

            if (updated > 0) {
                // 3. 记录操作日志
                recordOperationLog("SYSTEM", "系统管理员", "USER_ENABLE",
                        "用户", userId, "启用用户: " + user.getUserName());

                log.info("用户启用成功: userId={}", userId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("启用用户失败: userId={}", userId, e);
            return false;
        }
    }
    
    @Override
    public boolean kickUser(String userId) {
        log.info("踢用户下线: userId={}", userId);
        try {
            // 1. 查询用户信息
            LambdaQueryWrapper<ImUserDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImUserDO::getUserId, userId);
            ImUserDO user = imUserMapper.selectOne(wrapper);

            if (user == null) {
                log.warn("用户不存在，无法踢下线: userId={}", userId);
                return false;
            }

            // 2. 删除Redis中的在线状态（从 Hash 中删除）
            // userLogin:status: 是一个 Hash，Field 是用户ID，Value 是状态值
            // 【关键修复】使用默认Codec删除字段（与Lua脚本保持一致，不使用StringCodec）
            redissonUtils.deleteHash(USER_LOGIN_STATUS_KEY, userId);

            // 3. TODO: 通过gRPC通知im-connect断开该用户连接
            // 此处需要扩展gRPC接口来支持踢用户下线功能
            // 暂时只通过Redis删除在线状态，用户下次请求时会被拦截

            // 4. 记录操作日志
            recordOperationLog("SYSTEM", "系统管理员", "USER_KICK",
                    "用户", userId, "踢用户下线: " + user.getUserName());

            log.info("踢用户下线成功: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.error("踢用户下线失败: userId={}", userId, e);
            return false;
        }
    }
    
    @Override
    public boolean isUserOnline(String userId) {
        try {
            // 【调试】打印用户ID
            log.info("【DEBUG-isUserOnline】检查用户在线状态: userId={}", userId);
            // 使用默认Codec（与Lua脚本保持一致）
            boolean isOnline = redissonUtils.existsHash(USER_LOGIN_STATUS_KEY, userId);
            log.info("【DEBUG-isUserOnline】检查结果: userId={}, isOnline={}", userId, isOnline);
            return isOnline;
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

        // 手动映射注册时间：register_time -> createTime（前端期望createTime显示注册时间）
        vo.setCreateTime(user.getRegisterTime());

        // 手机号脱敏
        if (StringUtils.hasText(user.getPhone()) && user.getPhone().length() >= 11) {
            vo.setPhone(user.getPhone().substring(0, 3) + "****" + user.getPhone().substring(7));
        }

        // 性别描述
        vo.setSexDesc(getSexDesc(user.getSex()));

        // 用户状态描述
        vo.setStatus(getUserStatus(user.getStatus()));
        vo.setStatusDesc(getUserStatusDesc(user.getStatus()));

        // 终端类型描述
        vo.setTerminalTypeDesc(getTerminalTypeDesc(user.getRegisterTerminalType()));

        //设置在线状态
        vo.setOnline(isUserOnline(user.getUserId()));

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

    private Integer getUserStatus(Integer status) {
        // 如果status为null，默认返回0（正常）
        return status == null ? 0 : status;
    }

    private String getUserStatusDesc(Integer status) {
        if (status == null || status == 0) return "正常";
        if (status == 1) return "禁用";
        return "未知";
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

    /**
     * 记录操作日志
     *
     * @param adminId 管理员ID
     * @param adminName 管理员名称
     * @param operationType 操作类型
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param desc 操作描述
     */
    private void recordOperationLog(String adminId, String adminName, String operationType,
                                     String targetType, String targetId, String desc) {
        try {
            operationLogService.recordLog(
                    adminId,
                    adminName,
                    operationType,
                    targetType,
                    targetId,
                    desc,
                    "127.0.0.1", // TODO: 从请求上下文获取真实IP
                    null,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }
}
