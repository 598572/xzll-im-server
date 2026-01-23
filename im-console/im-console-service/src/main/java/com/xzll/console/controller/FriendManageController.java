package com.xzll.console.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.dto.FriendQueryDTO;
import com.xzll.console.service.FriendManageService;
import com.xzll.console.vo.FriendRelationVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友管理Controller
 */
@RestController
@RequestMapping("/api/console/friend")
public class FriendManageController {
    
    @Resource
    private FriendManageService friendManageService;
    
    /**
     * 分页查询好友关系
     */
    @PostMapping("/page")
    public WebBaseResponse<Page<FriendRelationVO>> pageFriendRelations(@RequestBody FriendQueryDTO queryDTO) {
        Page<FriendRelationVO> result = friendManageService.pageFriendRelations(queryDTO);
        return WebBaseResponse.returnResultSuccess(result);
    }
    
    /**
     * 获取用户的好友列表
     */
    @GetMapping("/list/{userId}")
    public WebBaseResponse<List<FriendRelationVO>> getUserFriends(@PathVariable String userId) {
        List<FriendRelationVO> result = friendManageService.getUserFriends(userId);
        return WebBaseResponse.returnResultSuccess(result);
    }
    
    /**
     * 获取好友关系详情
     */
    @GetMapping("/detail/{id}")
    public WebBaseResponse<FriendRelationVO> getRelationDetail(@PathVariable Long id) {
        FriendRelationVO result = friendManageService.getRelationDetail(id);
        if (result == null) {
            return WebBaseResponse.returnResultError("好友关系不存在");
        }
        return WebBaseResponse.returnResultSuccess(result);
    }
    
    /**
     * 检查两个用户是否为好友
     */
    @GetMapping("/check")
    public WebBaseResponse<Map<String, Object>> checkFriendship(
            @RequestParam String userId, 
            @RequestParam String friendId) {
        boolean isFriend = friendManageService.isFriend(userId, friendId);
        boolean isMutual = friendManageService.isMutualFriend(userId, friendId);
        
        Map<String, Object> result = Map.of(
                "isFriend", isFriend,
                "isMutualFriend", isMutual
        );
        return WebBaseResponse.returnResultSuccess(result);
    }
    
    /**
     * 获取用户的好友数量
     */
    @GetMapping("/count/{userId}")
    public WebBaseResponse<Long> countUserFriends(@PathVariable String userId) {
        Long count = friendManageService.countUserFriends(userId);
        return WebBaseResponse.returnResultSuccess(count);
    }
    
    /**
     * 获取系统总好友关系数
     */
    @GetMapping("/stats/total")
    public WebBaseResponse<Long> countTotalRelations() {
        Long count = friendManageService.countTotalRelations();
        return WebBaseResponse.returnResultSuccess(count);
    }
}
