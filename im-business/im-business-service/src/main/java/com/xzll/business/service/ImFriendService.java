package com.xzll.business.service;

import com.xzll.common.pojo.request.*;
import com.xzll.common.pojo.response.FriendInfoVO;
import com.xzll.common.pojo.response.FriendRequestVO;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友服务接口
 */
public interface ImFriendService {

    /**
     * 发送好友申请
     * 
     * @param ao 好友申请请求
     * @return 申请ID
     */
    String sendFriendRequest(FriendRequestSendAO ao);

    /**
     * 处理好友申请
     * 
     * @param ao 处理好友申请请求
     * @return 处理结果
     */
    boolean handleFriendRequest(FriendRequestHandleAO ao);

    /**
     * 查询好友申请列表
     * 
     * @param ao 查询好友申请列表请求
     * @return 好友申请列表
     */
    List<FriendRequestVO> findFriendRequestList(FriendRequestListAO ao);

    /**
     * 查询好友列表
     * 
     * @param ao 查询好友列表请求
     * @return 好友列表
     */
    List<FriendInfoVO> findFriendList(FriendListAO ao);

    /**
     * 删除好友
     * 
     * @param userId 用户ID
     * @param friendId 好友ID
     * @return 删除结果
     */
    boolean deleteFriend(String userId, String friendId);

    /**
     * 拉黑好友
     * 
     * @param userId 用户ID
     * @param friendId 好友ID
     * @param blackFlag 拉黑标志：true-拉黑，false-取消拉黑
     * @return 操作结果
     */
    boolean blockFriend(String userId, String friendId, boolean blackFlag);

}
