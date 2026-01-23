package com.xzll.console.controller;

import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.AdminDO;
import com.xzll.console.mapper.AdminMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 测试Controller - 用于调试
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    @Autowired
    private AdminMapper adminMapper;

    /**
     * 测试数据库连接
     */
    @GetMapping("/db")
    public WebBaseResponse<String> testDb() {
        try {
            Long count = adminMapper.selectCount(null);
            return WebBaseResponse.returnResultSuccess("数据库连接正常，管理员数量: " + count);
        } catch (Exception e) {
            log.error("数据库测试失败", e);
            return WebBaseResponse.returnResultError("数据库连接失败: " + e.getMessage());
        }
    }

    /**
     * 测试查询admin用户
     */
    @GetMapping("/admin")
    public WebBaseResponse<AdminDO> testAdmin() {
        try {
            LambdaQueryWrapper<AdminDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AdminDO::getUsername, "admin");
            AdminDO admin = adminMapper.selectOne(wrapper);

            if (admin == null) {
                return WebBaseResponse.returnResultError("admin用户不存在");
            }

            // 隐藏密码
            admin.setPassword("******");
            return WebBaseResponse.returnResultSuccess(admin);
        } catch (Exception e) {
            log.error("查询admin失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }
}
