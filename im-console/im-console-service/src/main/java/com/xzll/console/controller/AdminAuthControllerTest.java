package com.xzll.console.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.AdminDO;
import com.xzll.console.mapper.AdminMapper;
import com.xzll.console.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 临时测试Controller - 用于调试登录问题
 * 测试完成后请删除此文件
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/test")
@CrossOrigin(origins = "*")
public class AdminAuthControllerTest {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration:604800000}")
    private Long jwtExpiration;

    /**
     * 临时：跳过密码验证的登录接口
     * 仅用于测试，生产环境请删除！
     */
    @PostMapping("/login-no-password")
    public WebBaseResponse<Object> loginNoPassword(@RequestBody java.util.Map<String, String> params, HttpServletRequest request) {
        try {
            String username = params.get("username");
            String ip = getClientIp(request);

            log.info("【测试接口】无密码登录请求: username={}, ip={}", username, ip);

            // 1. 查询管理员
            LambdaQueryWrapper<AdminDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AdminDO::getUsername, username);
            AdminDO admin = adminMapper.selectOne(wrapper);

            if (admin == null) {
                return WebBaseResponse.returnResultError("用户不存在");
            }

            log.info("【测试接口】找到用户: adminId={}, status={}", admin.getAdminId(), admin.getStatus());

            // 2. 检查状态
            if (admin.getStatus() == 0) {
                return WebBaseResponse.returnResultError("账号已被禁用");
            }

            // 3. 生成Token
            String roleCode = "SUPER_ADMIN"; // 简化处理
            String token = jwtUtil.generateToken(admin.getAdminId(), admin.getUsername(), roleCode);

            log.info("【测试接口】Token生成成功: username={}", username);

            // 4. 构造返回结果
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("token", token);
            result.put("adminId", admin.getAdminId());
            result.put("username", admin.getUsername());
            result.put("realName", admin.getRealName());
            result.put("avatar", admin.getAvatar());
            result.put("roleId", admin.getRoleId());
            result.put("roleCode", roleCode);
            result.put("roleName", "超级管理员");
            result.put("expiresIn", jwtExpiration);

            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("【测试接口】登录失败", e);
            return WebBaseResponse.returnResultError("登录失败: " + e.getMessage());
        }
    }

    /**
     * 生成BCrypt密码哈希
     * GET /api/admin/test/generate-hash?password=admin123
     */
    @GetMapping("/generate-hash")
    public WebBaseResponse<String> generateHash(@RequestParam String password) {
        try {
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

            String hash = encoder.encode(password);
            boolean matches = encoder.matches(password, hash);

            String result = "原始密码: " + password + "\n" +
                           "BCrypt哈希: " + hash + "\n" +
                           "验证结果: " + matches;

            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            return WebBaseResponse.returnResultError("生成失败: " + e.getMessage());
        }
    }

    /**
     * 验证密码
     * POST /api/admin/test/verify-hash
     * Body: {"password":"admin123", "hash":"$2a$10$..."}
     */
    @PostMapping("/verify-hash")
    public WebBaseResponse<Boolean> verifyHash(@RequestBody java.util.Map<String, String> params) {
        try {
            String password = params.get("password");
            String hash = params.get("hash");

            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

            boolean matches = encoder.matches(password, hash);

            return WebBaseResponse.returnResultSuccess(matches);
        } catch (Exception e) {
            return WebBaseResponse.returnResultError("验证失败: " + e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
