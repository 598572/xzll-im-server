package com.xzll.common.util.msgId;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author: hzz
 * @Date: 2025/10/30
 * @Description: 压缩雪花算法用户ID使用示例
 */
public class CompactUserIdExample {

    public static void main(String[] args) {
        // 初始化服务
        SnowflakeIdService snowflakeService = new SnowflakeIdService();

        System.out.println("=================== 用户ID生成对比 ===================");

        // 【旧版本】- 原版雪花算法
        System.out.println("【旧版本 - 原版雪花算法】");
        String oldUserId = snowflakeService.nextId() + "";
        System.out.println("原版用户ID: " + oldUserId);
        System.out.println("长度: " + oldUserId.length() + " 位数字");

        // 【新版本】- 压缩雪花算法
        System.out.println("\n【新版本 - 压缩雪花算法】");
        String newUserId = snowflakeService.generateCompactUserId();
        System.out.println("压缩用户ID: " + newUserId);
        System.out.println("长度: " + newUserId.length() + " 位数字");

        // 长度对比
        int reduction = oldUserId.length() - newUserId.length();
        double reductionPercent = (double) reduction / oldUserId.length() * 100;
        System.out.println("长度减少: " + reduction + " 位数字 (" + String.format("%.1f", reductionPercent) + "%)");

        System.out.println("\n================== 时间特性验证 ==================");

        // 解析用户注册时间
        long registrationTime = SnowflakeIdService.extractUserRegistrationTime(newUserId);
        System.out.println("用户注册时间: " + new Date(registrationTime));

        // 详细解析
        System.out.println("\n================== 详细信息解析 ==================");
        SnowflakeIdService.parseCompactUserId(newUserId);

        System.out.println("\n================== 批量生成测试 ==================");

        // 批量生成用户ID
        List<String> batchUserIds = snowflakeService.generateBatchCompactUserId(5);
        System.out.println("批量生成5个用户ID:");
        for (int i = 0; i < batchUserIds.size(); i++) {
            String userId = batchUserIds.get(i);
            long regTime = SnowflakeIdService.extractUserRegistrationTime(userId);
            System.out.println(String.format("  用户%d: %s (注册时间: %s)",
                i + 1, userId, new Date(regTime)));
        }

        System.out.println("\n================== 唯一性验证测试 ==================");

        // 大批量生成测试唯一性
        int testCount = 10000;
        Set<String> uniqueIds = new HashSet<>();
        List<String> batchIds = snowflakeService.generateBatchCompactUserId(testCount);

        for (String id : batchIds) {
            if (!uniqueIds.add(id)) {
                System.out.println("❌ 发现重复ID: " + id);
                break;
            }
        }

        System.out.println("✅ 批量生成 " + testCount + " 个ID，唯一性验证:");
        System.out.println("  - 生成总数: " + batchIds.size());
        System.out.println("  - 唯一总数: " + uniqueIds.size());
        System.out.println("  - 重复数量: " + (batchIds.size() - uniqueIds.size()));
        System.out.println("  - 唯一率: " + String.format("%.4f%%",
            (uniqueIds.size() * 100.0 / batchIds.size())));

        System.out.println("\n================== 有效性验证 ==================");

        // 验证ID有效性
        System.out.println("用户ID有效性检查:");
        System.out.println("  " + newUserId + " -> " +
            (SnowflakeIdService.isValidCompactUserId(newUserId) ? "✅ 有效" : "❌ 无效"));
        System.out.println("  invalid123 -> " +
            (SnowflakeIdService.isValidCompactUserId("invalid123") ? "✅ 有效" : "❌ 无效"));

        System.out.println("\n================== 性能对比 ==================");

        // 性能测试
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            snowflakeService.generateCompactUserId();
        }
        long endTime = System.currentTimeMillis();

        System.out.println("生成1000个压缩用户ID耗时: " + (endTime - startTime) + "ms");
        System.out.println("平均每个ID耗时: " + String.format("%.3f", (endTime - startTime) / 1000.0) + "ms");

        System.out.println("\n================== 算法统计信息 ==================");
        System.out.println(snowflakeService.getCompactUserIdStats());

        System.out.println("\n================== 使用建议 ==================");
        System.out.println("✅ 优势对比:");
        System.out.println("  - ID长度减少约" + String.format("%.0f", reductionPercent) + "%");
        System.out.println("  - 保留完整时间特性（可解析注册时间）");
        System.out.println("  - 136年可用期限（2022-2158年）");
        System.out.println("  - 每秒可生成64个唯一ID");
        System.out.println("  - 纯数字格式，用户友好");
        System.out.println("\n🔧 集成方式:");
        System.out.println("  // 旧方式");
        System.out.println("  user.setUserId(snowflakeService.nextId() + \"\");");
        System.out.println("  // 新方式");
        System.out.println("  user.setUserId(snowflakeService.generateCompactUserId());");

        System.out.println("\n📊 实际应用场景:");
        simulateUserRegistration(snowflakeService);
    }

    /**
     * 模拟用户注册场景
     */
    private static void simulateUserRegistration(SnowflakeIdService service) {
        System.out.println("模拟用户注册:");

        String[] usernames = {"张三", "李四", "王五", "赵六"};

        for (String username : usernames) {
            String userId = service.generateCompactUserId();
            long registrationTime = SnowflakeIdService.extractUserRegistrationTime(userId);

            System.out.println(String.format("  用户 %s 注册成功: ID=%s, 时间=%s",
                username, userId, new Date(registrationTime)));

            // 模拟注册间隔
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
