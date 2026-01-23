package com.xzll.console;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码生成工具
 * 用于生成BCrypt加密的密码
 */
public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 生成密码哈希
        String password = "admin123";
        String hash = encoder.encode(password);

        System.out.println("原始密码: " + password);
        System.out.println("BCrypt哈希: " + hash);

        // 验证密码
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + matches);

        // 生成多个哈希（BCrypt每次生成的哈希都不同，但都能验证成功）
        System.out.println("\n生成多个哈希示例:");
        for (int i = 0; i < 5; i++) {
            String h = encoder.encode(password);
            System.out.println(h);
        }
    }
}
