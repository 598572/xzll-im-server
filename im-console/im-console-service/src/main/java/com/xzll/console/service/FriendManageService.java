package com.xzll.console.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.dto.FriendQueryDTO;
import com.xzll.console.vo.FriendRelationVO;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友管理服务接口
 */
public interface FriendManageService {
    
    /**
     * 分页查询好友关系
     */
    Page<FriendRelationVO> pageFriendRelations(FriendQueryDTO queryDTO);
    
    /**
     * 获取用户的好友列表
     */
    List<FriendRelationVO> getUserFriends(String userId);
    
    /**
     * 获取好友关系详情
     */
    FriendRelationVO getRelationDetail(Long id);
    
    /**
     * 检查两个用户是否为好友
     */
    boolean isFriend(String userId, String friendId);
    
    /**
     * 统计总好友关系数
     */
    Long countTotalRelations();
    
    /**
     * 统计用户的好友数
     */
    Long countUserFriends(String userId);
    
    /**
     * 获取互为好友的关系（双向验证）
     */
    boolean isMutualFriend(String userId, String friendId);
}
