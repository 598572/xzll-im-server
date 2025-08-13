package com.xzll.auth.service;

import com.xzll.auth.domain.UserDTO;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2025/1/8 18:00:55
 * @Description:
 */
public interface UserService {

    /**
     * 注册用户
     *
     * @param userDTO 用户信息
     * @return 是否注册成功
     */
    boolean registerUser(UserDTO userDTO);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return UserDTO
     */
    UserDTO getUserById(String userId);

    /**
     * 更新用户信息
     *
     * @param userDTO 用户信息
     * @return 是否更新成功
     */
    boolean updateUser(UserDTO userDTO);

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteUser(String userId);

    /**
     * 分页查询用户列表
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param username 用户名（可选）
     * @return 用户列表
     */
    List<UserDTO> listUsers(Integer pageNum, Integer pageSize, String username);

    /**
     * 修改用户密码
     *
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    boolean updatePassword(String userId, String oldPassword, String newPassword);

    /**
     * 重置用户密码（管理员功能）
     *
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 是否重置成功
     */
    boolean resetPassword(String userId, String newPassword);
}
