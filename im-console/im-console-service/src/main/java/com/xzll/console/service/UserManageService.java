package com.xzll.console.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.dto.UserQueryDTO;
import com.xzll.console.entity.ImUserDO;
import com.xzll.console.vo.UserVO;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户管理服务接口
 */
public interface UserManageService {
    
    /**
     * 分页查询用户列表
     *
     * @param queryDTO 查询条件
     * @return 用户列表
     */
    Page<UserVO> pageUsers(UserQueryDTO queryDTO);
    
    /**
     * 根据用户ID获取用户详情
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserVO getUserDetail(String userId);
    
    /**
     * 搜索用户
     *
     * @param keyword 关键词
     * @return 用户列表
     */
    List<UserVO> searchUsers(String keyword);
    
    /**
     * 禁用用户
     *
     * @param userId 用户ID
     * @param reason 禁用原因
     * @return 是否成功
     */
    boolean disableUser(String userId, String reason);
    
    /**
     * 启用用户
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean enableUser(String userId);
    
    /**
     * 踢用户下线
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean kickUser(String userId);
    
    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    boolean isUserOnline(String userId);
    
    /**
     * 获取用户的好友数量
     *
     * @param userId 用户ID
     * @return 好友数量
     */
    Long getFriendCount(String userId);
}
