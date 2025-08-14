package com.xzll.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/8/14 22:20:00
 * @Description: 接口权限验证配置类
 * <p>
 * 功能：
 * 1. 读取接口权限验证开关配置
 * 2. 管理绕过接口权限验证的路径
 * 3. 支持开发调试时绕过接口权限验证（但保留Token认证）
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthConfig {

    /**
     * 是否启用接口权限验证开关
     * true: 启用接口权限验证（生产环境）- 需要检查用户角色权限
     * false: 绕过接口权限验证（开发调试环境）- 只验证Token，不检查角色权限
     */
    private boolean enablePermissionCheck = true;

    /**
     * 绕过接口权限验证的路径前缀
     * 当enablePermissionCheck为false时，这些路径也会被绕过
     * 注意：Token认证仍然会进行，只是不检查用户是否有特定角色权限
     */
    private List<String> bypassPaths;

    /**
     * 检查指定路径是否需要绕过接口权限验证
     *
     * @param path 请求路径
     * @return true表示需要绕过接口权限验证，false表示需要正常验证
     */
    public boolean shouldBypassPermissionCheck(String path) {
        // 如果全局开关关闭，直接绕过
        if (!enablePermissionCheck) {
            return true;
        }

        // 检查是否在绕过路径列表中
        if (bypassPaths != null) {
            for (String bypassPath : bypassPaths) {
                if (path.startsWith(bypassPath)) {
                    return true;
                }
            }
        }

        return false;
    }
}
