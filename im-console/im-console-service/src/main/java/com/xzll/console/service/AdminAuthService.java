package com.xzll.console.service;

import com.xzll.console.dto.LoginDTO;
import com.xzll.console.vo.LoginVO;

/**
 * 管理员认证服务
 *
 * @author xzll
 */
public interface AdminAuthService {

    /**
     * 管理员登录
     *
     * @param dto 登录请求
     * @param ip 请求IP
     * @return 登录响应
     */
    LoginVO login(LoginDTO dto, String ip);

    /**
     * 获取当前登录管理员信息
     *
     * @param adminId 管理员ID
     * @return 登录响应
     */
    LoginVO getCurrentAdminInfo(String adminId);

    /**
     * 登出
     *
     * @param adminId 管理员ID
     */
    void logout(String adminId);
}
