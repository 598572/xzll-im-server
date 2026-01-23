package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzll.console.dto.LoginDTO;
import com.xzll.console.entity.AdminDO;
import com.xzll.console.entity.AdminRoleDO;
import com.xzll.console.mapper.AdminMapper;
import com.xzll.console.mapper.AdminRoleMapper;
import com.xzll.console.service.AdminAuthService;
import com.xzll.console.util.JwtUtil;
import com.xzll.console.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理员认证服务实现
 *
 * @author xzll
 */
@Slf4j
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration:604800000}")
    private Long jwtExpiration;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginVO login(LoginDTO dto, String ip) {
        log.info("登录请求: username={}, ip={}", dto.getUsername(), ip);

        // 1. 查询管理员
        LambdaQueryWrapper<AdminDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminDO::getUsername, dto.getUsername());
        AdminDO admin = adminMapper.selectOne(wrapper);

        if (admin == null) {
            log.warn("用户不存在: username={}", dto.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        log.info("找到用户: adminId={}, status={}", admin.getAdminId(), admin.getStatus());

        // 2. 验证密码（BCrypt）
        boolean passwordMatch = passwordEncoder.matches(dto.getPassword(), admin.getPassword());
        log.info("密码验证结果: {}, 输入密码长度: {}, 数据库密码前缀: {}",
                passwordMatch, dto.getPassword().length(), admin.getPassword().substring(0, 30));

        if (!passwordMatch) {
            log.warn("密码验证失败: username={}", dto.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // 3. 检查状态
        if (admin.getStatus() == 0) {
            log.warn("账号已被禁用: username={}", dto.getUsername());
            throw new RuntimeException("账号已被禁用");
        }

        log.info("密码验证成功: username={}", dto.getUsername());

        // 4. 查询角色
        AdminRoleDO role = null;
        if (admin.getRoleId() != null) {
            role = adminRoleMapper.selectById(admin.getRoleId());
        }

        // 5. 生成Token
        String roleCode = role != null ? role.getRoleCode() : "UNKNOWN";
        String token = jwtUtil.generateToken(admin.getAdminId(), admin.getUsername(), roleCode);

        // 6. 更新最后登录信息
        admin.setLastLoginTime(LocalDateTime.now());
        admin.setLastLoginIp(ip);
        adminMapper.updateById(admin);

        // 7. 构造返回结果
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setAdminId(admin.getAdminId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setAvatar(admin.getAvatar());
        vo.setRoleId(admin.getRoleId());
        vo.setRoleCode(roleCode);
        vo.setRoleName(role != null ? role.getRoleName() : "");
        vo.setExpiresIn(jwtExpiration);

        log.info("管理员登录成功: username={}, ip={}", dto.getUsername(), ip);

        return vo;
    }

    @Override
    public LoginVO getCurrentAdminInfo(String adminId) {
        AdminDO admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminDO>().eq(AdminDO::getAdminId, adminId)
        );

        if (admin == null) {
            throw new RuntimeException("管理员不存在");
        }

        AdminRoleDO role = null;
        if (admin.getRoleId() != null) {
            role = adminRoleMapper.selectById(admin.getRoleId());
        }

        LoginVO vo = new LoginVO();
        vo.setAdminId(admin.getAdminId());
        vo.setUsername(admin.getUsername());
        vo.setRealName(admin.getRealName());
        vo.setAvatar(admin.getAvatar());
        vo.setRoleId(admin.getRoleId());
        vo.setRoleCode(role != null ? role.getRoleCode() : "");
        vo.setRoleName(role != null ? role.getRoleName() : "");

        return vo;
    }

    @Override
    public void logout(String adminId) {
        log.info("管理员登出: adminId={}", adminId);
        // 如果使用Redis存储Token，这里需要删除Token
        // 当前实现是无状态的JWT，登出只需前端删除Token即可
    }
}
