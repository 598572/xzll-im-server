package com.xzll.console.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.dto.LoginDTO;
import com.xzll.console.service.AdminAuthService;
import com.xzll.console.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理员认证Controller
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@CrossOrigin(origins = "*")
public class AdminAuthController {

    @Autowired
    private AdminAuthService adminAuthService;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public WebBaseResponse<LoginVO> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            LoginVO vo = adminAuthService.login(dto, ip);
            return WebBaseResponse.returnResultSuccess(vo);
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 获取当前登录管理员信息
     */
    @GetMapping("/info")
    public WebBaseResponse<LoginVO> getCurrentAdminInfo(@RequestHeader("X-Admin-Id") String adminId) {
        try {
            LoginVO vo = adminAuthService.getCurrentAdminInfo(adminId);
            return WebBaseResponse.returnResultSuccess(vo);
        } catch (Exception e) {
            log.error("获取管理员信息失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public WebBaseResponse<Void> logout(@RequestHeader("X-Admin-Id") String adminId) {
        try {
            adminAuthService.logout(adminId);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("登出失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
