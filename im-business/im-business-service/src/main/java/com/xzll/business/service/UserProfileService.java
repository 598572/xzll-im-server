package com.xzll.business.service;

import com.xzll.common.pojo.request.UpdateUserProfileAO;
import com.xzll.common.pojo.response.UserProfileVO;

/**
 * 用户个人信息服务接口
 */
public interface UserProfileService {

    /**
     * 根据用户ID获取用户个人信息
     * @param userId 用户ID（业务ID，不是主键）
     * @return 用户个人信息
     */
    UserProfileVO getUserProfileByUserId(String userId);

    /**
     * 更新用户个人信息
     * @param userId 用户ID
     * @param updateAO 更新参数
     * @return 更新后的用户信息
     */
    UserProfileVO updateUserProfile(String userId, UpdateUserProfileAO updateAO);

    /**
     * 更新用户头像URL
     * @param userId 用户ID
     * @param avatarUrl 头像URL
     * @return 是否更新成功
     */
    boolean updateUserAvatar(String userId, String avatarUrl);
}
