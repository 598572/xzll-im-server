package com.xzll.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xzll.business.mapper.ImUserMapper;
import com.xzll.business.service.UserProfileService;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.common.pojo.request.UpdateUserProfileAO;
import com.xzll.common.pojo.response.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 用户个人信息服务实现
 */
@Slf4j
@Service
public class UserProfileServiceImpl implements UserProfileService {

    @Resource
    private ImUserMapper imUserMapper;

    @Override
    public UserProfileVO getUserProfileByUserId(String userId) {
        log.info("根据userId获取用户个人信息，userId：{}", userId);
        
        try {
            // 1. 参数校验
            if (!StringUtils.hasText(userId)) {
                throw new IllegalArgumentException("用户ID不能为空");
            }
            
            // 2. 根据userId字段查询用户信息（不是主键查询）
            LambdaQueryWrapper<ImUserDO> queryWrapper = Wrappers.lambdaQuery(ImUserDO.class)
                    .eq(ImUserDO::getUserId, userId);
            
            ImUserDO userDO = imUserMapper.selectOne(queryWrapper);
            if (userDO == null) {
                throw new RuntimeException("用户不存在，userId：" + userId);
            }
            
            // 3. 转换为VO对象
            UserProfileVO userProfileVO = new UserProfileVO();
            BeanUtils.copyProperties(userDO, userProfileVO);
            
            log.info("获取用户个人信息成功，userId：{}", userId);
            return userProfileVO;
            
        } catch (Exception e) {
            log.error("获取用户个人信息失败，userId：{}", userId, e);
            throw new RuntimeException("获取用户信息失败：" + e.getMessage());
        }
    }

    @Override
    public UserProfileVO updateUserProfile(String userId, UpdateUserProfileAO updateAO) {
        log.info("更新用户个人信息，userId：{}，参数：{}", userId, updateAO);
        
        try {
            // 1. 参数校验
            if (!StringUtils.hasText(userId)) {
                throw new IllegalArgumentException("用户ID不能为空");
            }
            if (updateAO == null) {
                throw new IllegalArgumentException("更新参数不能为空");
            }
            
            // 2. 查询当前用户信息
            LambdaQueryWrapper<ImUserDO> queryWrapper = Wrappers.lambdaQuery(ImUserDO.class)
                    .eq(ImUserDO::getUserId, userId);
            
            ImUserDO existingUser = imUserMapper.selectOne(queryWrapper);
            if (existingUser == null) {
                throw new RuntimeException("用户不存在，userId：" + userId);
            }
            
            // 3. 构建更新条件和更新内容
            LambdaUpdateWrapper<ImUserDO> updateWrapper = Wrappers.lambdaUpdate(ImUserDO.class)
                    .eq(ImUserDO::getUserId, userId)
                    .set(ImUserDO::getUpdateTime, LocalDateTime.now());
            
            // 4. 设置需要更新的字段（只更新非空字段）
            boolean hasUpdate = false;
            if (StringUtils.hasText(updateAO.getUserName())) {
                updateWrapper.set(ImUserDO::getUserName, updateAO.getUserName());
                hasUpdate = true;
            }
            if (StringUtils.hasText(updateAO.getUserFullName())) {
                updateWrapper.set(ImUserDO::getUserFullName, updateAO.getUserFullName());
                hasUpdate = true;
            }
            if (StringUtils.hasText(updateAO.getPhone())) {
                updateWrapper.set(ImUserDO::getPhone, updateAO.getPhone());
                hasUpdate = true;
            }
            if (StringUtils.hasText(updateAO.getEmail())) {
                updateWrapper.set(ImUserDO::getEmail, updateAO.getEmail());
                hasUpdate = true;
            }
            if (updateAO.getSex() != null) {
                updateWrapper.set(ImUserDO::getSex, updateAO.getSex());
                hasUpdate = true;
            }
            
            if (!hasUpdate) {
                log.warn("没有需要更新的字段，userId：{}", userId);
                return getUserProfileByUserId(userId);
            }
            
            // 5. 执行更新
            int updateCount = imUserMapper.update(null, updateWrapper);
            if (updateCount <= 0) {
                throw new RuntimeException("更新用户信息失败，userId：" + userId);
            }
            
            // 6. 返回更新后的用户信息
            UserProfileVO result = getUserProfileByUserId(userId);
            log.info("更新用户个人信息成功，userId：{}", userId);
            return result;
            
        } catch (Exception e) {
            log.error("更新用户个人信息失败，userId：{}", userId, e);
            throw new RuntimeException("更新用户信息失败：" + e.getMessage());
        }
    }

    @Override
    public boolean updateUserAvatar(String userId, String avatarUrl) {
        log.info("更新用户头像，userId：{}，avatarUrl：{}", userId, avatarUrl);
        
        try {
            // 1. 参数校验
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(avatarUrl)) {
                throw new IllegalArgumentException("用户ID和头像URL不能为空");
            }
            
            // 2. 根据userId字段更新头像URL
            LambdaUpdateWrapper<ImUserDO> updateWrapper = Wrappers.lambdaUpdate(ImUserDO.class)
                    .eq(ImUserDO::getUserId, userId)
                    .set(ImUserDO::getHeadImage, avatarUrl)
                    .set(ImUserDO::getUpdateTime, LocalDateTime.now());
            
            int updateCount = imUserMapper.update(null, updateWrapper);
            if (updateCount <= 0) {
                log.error("更新用户头像失败，userId：{}", userId);
                return false;
            }
            
            log.info("更新用户头像成功，userId：{}", userId);
            return true;
            
        } catch (Exception e) {
            log.error("更新用户头像异常，userId：{}", userId, e);
            return false;
        }
    }
}
