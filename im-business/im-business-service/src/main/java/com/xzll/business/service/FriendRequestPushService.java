package com.xzll.business.service;

import com.xzll.business.entity.mysql.ImFriendRequest;
import com.xzll.common.pojo.entity.ImUserDO;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友申请推送服务接口
 */
public interface FriendRequestPushService {

    /**
     * 推送新的好友申请
     *
     * @param friendRequest 好友申请记录
     * @param fromUser      申请人信息
     */
    void pushFriendRequest(ImFriendRequest friendRequest, ImUserDO fromUser);

    /**
     * 推送好友申请处理结果
     *
     * @param friendRequest 好友申请记录
     * @param fromUser      申请人信息
     * @param toUser        被申请人信息
     */
    void pushFriendRequestHandleResult(ImFriendRequest friendRequest, ImUserDO fromUser, ImUserDO toUser);

}
