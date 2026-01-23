package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.dto.FriendQueryDTO;
import com.xzll.console.entity.ImFriendRelationDO;
import com.xzll.console.entity.ImUserDO;
import com.xzll.console.mapper.ImFriendRelationMapper;
import com.xzll.console.mapper.ImUserMapper;
import com.xzll.console.service.FriendManageService;
import com.xzll.console.vo.FriendRelationVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友管理服务实现
 */
@Slf4j
@Service
public class FriendManageServiceImpl implements FriendManageService {
    
    @Resource
    private ImFriendRelationMapper friendRelationMapper;
    
    @Resource
    private ImUserMapper imUserMapper;
    
    @Override
    public Page<FriendRelationVO> pageFriendRelations(FriendQueryDTO queryDTO) {
        Page<ImFriendRelationDO> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        
        LambdaQueryWrapper<ImFriendRelationDO> wrapper = new LambdaQueryWrapper<>();
        
        // 默认查询未删除的好友关系
        wrapper.eq(ImFriendRelationDO::getDelFlag, 0);
        
        // 关键词搜索
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.and(w -> w
                    .like(ImFriendRelationDO::getUserId, queryDTO.getKeyword())
                    .or().like(ImFriendRelationDO::getFriendId, queryDTO.getKeyword())
            );
        }
        
        // 精确查询
        if (StringUtils.hasText(queryDTO.getUserId())) {
            wrapper.eq(ImFriendRelationDO::getUserId, queryDTO.getUserId());
        }
        if (StringUtils.hasText(queryDTO.getFriendId())) {
            wrapper.eq(ImFriendRelationDO::getFriendId, queryDTO.getFriendId());
        }
        if (queryDTO.getBlackFlag() != null) {
            wrapper.eq(ImFriendRelationDO::getBlackFlag, queryDTO.getBlackFlag());
        }
        
        wrapper.orderByDesc(ImFriendRelationDO::getCreateTime);
        
        Page<ImFriendRelationDO> result = friendRelationMapper.selectPage(page, wrapper);
        
        // 收集所有用户ID用于批量查询用户名
        List<String> userIds = result.getRecords().stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getUserId(), r.getFriendId()))
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, String> userNameMap = getUserNameMap(userIds);
        
        // 转换为VO
        Page<FriendRelationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<FriendRelationVO> voList = result.getRecords().stream()
                .map(r -> convertToVO(r, userNameMap))
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        
        return voPage;
    }
    
    @Override
    public List<FriendRelationVO> getUserFriends(String userId) {
        List<ImFriendRelationDO> relations = friendRelationMapper.selectFriendsByUserId(userId);
        
        List<String> friendIds = relations.stream()
                .map(ImFriendRelationDO::getFriendId)
                .distinct()
                .collect(Collectors.toList());
        
        // 添加用户自己的ID
        friendIds.add(userId);
        
        Map<String, String> userNameMap = getUserNameMap(friendIds);
        
        return relations.stream()
                .map(r -> convertToVO(r, userNameMap))
                .collect(Collectors.toList());
    }
    
    @Override
    public FriendRelationVO getRelationDetail(Long id) {
        ImFriendRelationDO relation = friendRelationMapper.selectById(id);
        if (relation == null) {
            return null;
        }
        
        List<String> userIds = List.of(relation.getUserId(), relation.getFriendId());
        Map<String, String> userNameMap = getUserNameMap(userIds);
        
        return convertToVO(relation, userNameMap);
    }
    
    @Override
    public boolean isFriend(String userId, String friendId) {
        LambdaQueryWrapper<ImFriendRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImFriendRelationDO::getUserId, userId)
                .eq(ImFriendRelationDO::getFriendId, friendId)
                .eq(ImFriendRelationDO::getDelFlag, 0);
        
        return friendRelationMapper.selectCount(wrapper) > 0;
    }
    
    @Override
    public Long countTotalRelations() {
        try {
            return friendRelationMapper.countTotalRelations();
        } catch (Exception e) {
            log.error("统计总好友关系数失败", e);
            return 0L;
        }
    }
    
    @Override
    public Long countUserFriends(String userId) {
        return friendRelationMapper.countFriendsByUserId(userId);
    }
    
    @Override
    public boolean isMutualFriend(String userId, String friendId) {
        // 检查双向好友关系
        boolean aToB = isFriend(userId, friendId);
        boolean bToA = isFriend(friendId, userId);
        return aToB && bToA;
    }
    
    /**
     * 批量获取用户名映射
     */
    private Map<String, String> getUserNameMap(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        
        LambdaQueryWrapper<ImUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ImUserDO::getUserId, userIds)
                .select(ImUserDO::getUserId, ImUserDO::getUserName);
        
        List<ImUserDO> users = imUserMapper.selectList(wrapper);
        
        return users.stream()
                .collect(Collectors.toMap(ImUserDO::getUserId, ImUserDO::getUserName, (v1, v2) -> v1));
    }
    
    /**
     * 将DO转换为VO
     */
    private FriendRelationVO convertToVO(ImFriendRelationDO relation, Map<String, String> userNameMap) {
        FriendRelationVO vo = new FriendRelationVO();
        BeanUtils.copyProperties(relation, vo);
        
        // 设置用户名
        vo.setUserName(userNameMap.getOrDefault(relation.getUserId(), ""));
        vo.setFriendName(userNameMap.getOrDefault(relation.getFriendId(), ""));
        
        // 设置拉黑状态描述
        vo.setBlackFlagDesc(relation.getBlackFlag() == 1 ? "已拉黑" : "正常");
        
        return vo;
    }
}
