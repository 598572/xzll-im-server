package com.xzll.auth.controller;

import com.xzll.auth.config.Oauth2Config;
import com.xzll.auth.constant.AuthConstant;
import com.xzll.auth.domain.Oauth2TokenDto;
import com.xzll.auth.domain.TokenRequest;
import com.xzll.auth.domain.LogoutRequest;
import com.xzll.auth.domain.BatchLogoutRequest;
import com.xzll.auth.domain.RefreshTokenRequest;
import com.xzll.auth.domain.SecurityUser;
import com.xzll.auth.service.JwtTokenService;
import com.xzll.auth.util.DeviceTypeContext;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.constant.enums.ImTerminalType;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 登录认证相关信息（Spring Boot 3.x + Spring Security 6.x 版本）
 * 替代原来基于 TokenEndpoint 的实现，使用 JwtTokenService 进行 JWT 生成和验证
 */
@RestController
@Slf4j
@RequestMapping("/oauth")
public class AuthController {

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private JwtTokenService jwtTokenService;

    @Resource
    private UserDetailsService userDetailsService;

    @Autowired
    private RedissonUtils redissonUtils;

    @Autowired
    private RedissonClient redissonClient;

    @Resource
    private Oauth2Config oauth2Config;

    /**
     * OAuth2登录认证接口（支持多端登录）
     * <p>
     * 登录流程：
     * 1. 验证请求参数（用户名、密码、device_type等）
     * 2. 使用 AuthenticationManager 进行用户认证
     * 3. 认证成功后使用 JwtTokenService 生成 JWT 访问令牌和刷新令牌
     * 4. 将token和用户ID存储到Redis中（支持多端）
     * 5. 返回token信息给客户端
     * <p>
     * 设备类型说明：
     * 1: android, 2: ios, 3: 小程序, 4: web
     * <p>
     * 必传参数：
     * - username: 用户名
     * - password: 密码
     * - device_type: 设备类型（1-4，必传）
     *
     * @param parameters 登录参数，包含：username、password、device_type等
     * @return 包含访问令牌的响应对象
     */
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public WebBaseResponse<Oauth2TokenDto> postAccessToken(@RequestParam Map<String, String> parameters) {
        try {
            // 第一步：验证请求参数
            if (parameters == null || parameters.isEmpty()) {
                log.error("登录参数为空");
                return WebBaseResponse.returnResultError("登录参数不能为空");
            }

            String username = parameters.get("username");
            String password = parameters.get("password");

            if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
                log.error("用户名或密码为空");
                return WebBaseResponse.returnResultError("用户名和密码不能为空");
            }

            ImTerminalType deviceType = getDeviceTypeFromParameters(parameters);
            // 验证设备类型是否在支持范围内
            if (!isValidDeviceType(deviceType)) {
                log.error("不支持的设备类型: {}", deviceType != null ? deviceType.getCode() : "null");
                return WebBaseResponse.returnResultError(getDeviceTypeErrorMessage(deviceType));
            }

            // 第二步：使用 AuthenticationManager 进行用户认证
            Authentication authentication;
            try {
                UsernamePasswordAuthenticationToken authRequest = 
                        new UsernamePasswordAuthenticationToken(username, password);
                authentication = authenticationManager.authenticate(authRequest);
            } catch (AuthenticationException e) {
                log.error("用户认证失败，用户名: {}", username, e);
                return WebBaseResponse.returnResultError("用户名或密码错误");
            }

            // 第三步：获取认证后的用户信息并生成Token
            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            
            // 验证用户ID
            if (securityUser.getId() == null) {
                log.error("用户ID为空，用户名: {}", username);
                return WebBaseResponse.returnResultError("用户数据异常");
            }

            // 生成访问令牌和刷新令牌
            String accessToken = jwtTokenService.generateAccessToken(securityUser, deviceType);
            String refreshToken = jwtTokenService.generateRefreshToken(securityUser, deviceType);

            // 第四步：构建返回给客户端的token信息
            Oauth2TokenDto oauth2TokenDto = Oauth2TokenDto.builder()
                    .token(accessToken)  // JWT访问令牌
                    .refreshToken(refreshToken)  // 刷新令牌
                    .expiresIn(oauth2Config.getTokenTimeOut())  // 过期时间（秒）
                    .tokenHead(AuthConstant.TOKEN_PREFIX).build();  // Token前缀 "Bearer "

            // 第五步：将token和用户ID存储到Redis中（支持多端）
            saveTokenToRedis(securityUser.getId().toString(), accessToken, deviceType);

            // 第六步：返回成功响应
            log.info("用户登录成功，用户ID: {}", securityUser.getId());
            return WebBaseResponse.returnResultSuccess(oauth2TokenDto);

        } catch (Exception e) {
            log.error("用户登录异常", e);
            return WebBaseResponse.returnResultError("登录失败：" + e.getMessage());
        }
    }

    /**
     * 将token和用户ID存储到Redis中（支持多端登录）
     * <p>
     * 存储策略：
     * 1. 使用分布式锁确保同一用户的token操作的原子性
     * 2. 支持多端登录：android、ios、小程序、web
     * 3. 每个端最多保留一个有效token，新登录会覆盖旧token
     * 4. 存储映射关系：user_token_key + userId + deviceType + token_hash -> 用户ID
     * 5. 设置过期时间，与token过期时间一致
     *
     * @param userId     用户ID
     * @param token      访问令牌
     * @param deviceType 设备类型
     */
    private void saveTokenToRedis(String userId, String token, ImTerminalType deviceType) {
        if (StringUtils.isBlank(userId)) {
            log.warn("用户ID为空，跳过Redis存储");
            return;
        }

        // 构建分布式锁的key
        String lockKey = "user_token_lock:" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 获取分布式锁，等待5秒，持有锁30秒
            boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取用户{}的token操作锁失败", userId);
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            // 第一步：删除该用户指定端的旧token
            deleteUserDeviceTokens(userId, deviceType);

            // 第二步：计算token的MD5哈希值
            String tokenMd5 = calculateTokenMd5(token);

            // 第三步：构建Redis key：USER_TOKEN_KEY + userId + deviceType + MD5(token)
            String redisKey = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + userId + ":" + deviceType.getCode() + ":" + tokenMd5;

            // 第四步：存储映射关系：key -> 用户ID，并设置过期时间
            redissonUtils.setString(redisKey, userId, oauth2Config.getTokenTimeOut(), TimeUnit.SECONDS);
            log.info("Token已保存到Redis，key: {}, deviceType: {}, tokenMd5: {}", redisKey, deviceType.getCode(), tokenMd5);

        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断");
        } catch (Exception e) {
            log.error("保存Token到Redis失败", e);
            throw new RuntimeException("保存Token失败");
        } finally {
            // 释放分布式锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Token刷新接口（支持多端）
     * <p>
     * 刷新流程：
     * 1. 验证刷新令牌和设备类型参数
     * 2. 验证刷新令牌的有效性
     * 3. 从刷新令牌中获取用户信息
     * 4. 重新生成新的访问令牌和刷新令牌
     * 5. 更新Redis中的token信息
     * 6. 返回新的token信息给客户端
     * <p>
     * 请求格式：
     * JSON格式：{"refreshToken": "xxx.yyy.zzz", "deviceType": 1}
     * <p>
     * 必传参数：
     * - refreshToken: 刷新令牌（必传）
     * - deviceType: 设备类型（1-4，必传）
     *
     * @param refreshTokenRequest 刷新token请求对象
     * @return 包含新访问令牌的响应对象
     */
    @PostMapping("/refresh")
    public WebBaseResponse<Oauth2TokenDto> refreshToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        try {
            // 第一步：验证刷新令牌参数
            if (StringUtils.isBlank(refreshTokenRequest.getRefreshToken())) {
                log.error("刷新令牌为空");
                return WebBaseResponse.returnResultError("刷新令牌不能为空");
            }

            // 验证设备类型参数
            if (refreshTokenRequest.getDeviceType() == null) {
                log.error("设备类型参数未传");
                return WebBaseResponse.returnResultError("设备类型参数不能为空");
            }

            // 验证设备类型是否在支持范围内
            if (!isValidDeviceType(refreshTokenRequest.getDeviceType())) {
                log.error("不支持的设备类型: {}", refreshTokenRequest.getDeviceType() != null ? refreshTokenRequest.getDeviceType().getCode() : "null");
                return WebBaseResponse.returnResultError(getDeviceTypeErrorMessage(refreshTokenRequest.getDeviceType()));
            }

            // 第二步：验证刷新令牌的有效性
            String oldRefreshToken = refreshTokenRequest.getRefreshToken();
            if (!jwtTokenService.isRefreshToken(oldRefreshToken)) {
                log.error("不是有效的刷新令牌");
                return WebBaseResponse.returnResultError("无效的刷新令牌");
            }

            // 从刷新令牌中获取用户信息
            Long userId = jwtTokenService.getUserIdFromToken(oldRefreshToken);
            String username = jwtTokenService.getUsernameFromToken(oldRefreshToken);
            
            if (userId == null || StringUtils.isBlank(username)) {
                log.error("无法从刷新令牌中获取用户信息");
                return WebBaseResponse.returnResultError("刷新令牌无效或已过期");
            }

            // 第三步：重新加载用户信息并生成新Token
            SecurityUser securityUser;
            try {
                securityUser = (SecurityUser) userDetailsService.loadUserByUsername(username);
            } catch (Exception e) {
                log.error("加载用户信息失败，用户名: {}", username, e);
                return WebBaseResponse.returnResultError("用户不存在或已禁用");
            }

            // 将设备类型存储到ThreadLocal中（兼容旧逻辑）
            DeviceTypeContext.setDeviceType(refreshTokenRequest.getDeviceType());
            
            try {
                // 生成新的访问令牌和刷新令牌
                String newAccessToken = jwtTokenService.generateAccessToken(securityUser, refreshTokenRequest.getDeviceType());
                String newRefreshToken = jwtTokenService.generateRefreshToken(securityUser, refreshTokenRequest.getDeviceType());

                // 第四步：构建返回给客户端的token信息
                Oauth2TokenDto oauth2TokenDto = Oauth2TokenDto.builder()
                        .token(newAccessToken)  // 新的JWT访问令牌
                        .refreshToken(newRefreshToken)  // 新的刷新令牌
                        .expiresIn(oauth2Config.getTokenTimeOut())  // 过期时间（秒）
                        .tokenHead(AuthConstant.TOKEN_PREFIX).build();  // Token前缀 "Bearer "

                // 第五步：更新Redis中的token信息（保持设备类型）
                saveTokenToRedis(securityUser.getId().toString(), newAccessToken, refreshTokenRequest.getDeviceType());

                // 第六步：返回成功响应
                log.info("Token刷新成功，用户ID: {}", securityUser.getId());
                return WebBaseResponse.returnResultSuccess(oauth2TokenDto);
            } finally {
                // 清除ThreadLocal
                DeviceTypeContext.clear();
            }

        } catch (Exception e) {
            log.error("Token刷新异常，refreshToken: {}", refreshTokenRequest.getRefreshToken(), e);
            return WebBaseResponse.returnResultError("Token刷新失败：" + e.getMessage());
        }
    }

    /**
     * 用户登出接口
     * <p>
     * 登出流程：
     * 1. 验证请求参数（userId、deviceType）
     * 2. 验证设备类型是否在支持范围内
     * 3. 删除该用户指定设备类型的所有token
     * 4. 返回登出结果
     *
     * @param logoutRequest 登出请求对象（包含userId和deviceType）
     * @return 登出结果
     */
    @PostMapping("/logout")
    public WebBaseResponse<String> logout(@RequestBody LogoutRequest logoutRequest) {
        try {
            // 第一步：验证用户ID是否为空
            if (StringUtils.isBlank(logoutRequest.getUserId())) {
                log.error("登出用户ID为空");
                return WebBaseResponse.returnResultError("用户ID不能为空");
            }

            // 第二步：验证设备类型参数
            if (logoutRequest.getDeviceType() == null) {
                log.error("设备类型参数未传");
                return WebBaseResponse.returnResultError("设备类型参数不能为空");
            }

            // 第三步：验证设备类型是否在支持范围内
            if (!isValidDeviceType(logoutRequest.getDeviceType())) {
                log.error("不支持的设备类型: {}", logoutRequest.getDeviceType() != null ? logoutRequest.getDeviceType().getCode() : "null");
                return WebBaseResponse.returnResultError(getDeviceTypeErrorMessage(logoutRequest.getDeviceType()));
            }

            // 第四步：删除该用户指定设备类型的所有token
            int deletedCount = deleteUserDeviceTokensForLogout(logoutRequest.getUserId(), logoutRequest.getDeviceType());
            
            if (deletedCount > 0) {
                log.info("用户登出成功，用户ID: {}, 设备类型: {}, 删除token数量: {}", 
                        logoutRequest.getUserId(), logoutRequest.getDeviceType().getDescription(), deletedCount);
                return WebBaseResponse.returnResultSuccess("登出成功，共登出" + deletedCount + "个token");
            } else {
                log.info("用户登出完成，用户ID: {}, 设备类型: {}, 该设备类型无有效token", 
                        logoutRequest.getUserId(), logoutRequest.getDeviceType().getDescription());
                return WebBaseResponse.returnResultSuccess("该设备类型无有效token");
            }

        } catch (Exception e) {
            log.error("用户登出异常", e);
            return WebBaseResponse.returnResultError("登出失败：" + e.getMessage());
        }
    }

    /**
     * 批量登出接口
     * <p>
     * 登出流程：
     * 1. 验证请求参数（userId）
     * 2. 删除该用户所有设备类型的所有token
     * 3. 返回登出结果
     *
     * @param batchLogoutRequest 批量登出请求对象（包含userId）
     * @return 登出结果
     */
    @PostMapping("/batch-logout")
    public WebBaseResponse<String> batchLogout(@RequestBody BatchLogoutRequest batchLogoutRequest) {
        try {
            // 第一步：验证用户ID是否为空
            if (StringUtils.isBlank(batchLogoutRequest.getUserId())) {
                log.error("批量登出用户ID为空");
                return WebBaseResponse.returnResultError("用户ID不能为空");
            }

            // 第二步：删除该用户所有设备类型的所有token
            int totalDeletedCount = 0;
            
            // 遍历所有支持的设备类型
            for (ImTerminalType deviceType : new ImTerminalType[]{ImTerminalType.ANDROID, ImTerminalType.IOS, ImTerminalType.MINI_PROGRAM, ImTerminalType.WEB}) {
                int deletedCount = deleteUserDeviceTokensForLogout(batchLogoutRequest.getUserId(), deviceType);
                totalDeletedCount += deletedCount;
            }
            
            if (totalDeletedCount > 0) {
                log.info("批量登出成功，用户ID: {}, 删除token总数: {}", batchLogoutRequest.getUserId(), totalDeletedCount);
                return WebBaseResponse.returnResultSuccess("批量登出成功，共登出" + totalDeletedCount + "个token");
            } else {
                log.info("批量登出完成，用户ID: {}, 无有效token", batchLogoutRequest.getUserId());
                return WebBaseResponse.returnResultSuccess("无有效token");
            }

        } catch (Exception e) {
            log.error("批量登出异常", e);
            return WebBaseResponse.returnResultError("批量登出失败：" + e.getMessage());
        }
    }

    /**
     * 验证 jwt Token（支持多端）
     * <p>
     * 网关已做认证，此接口主要用于不走网关的服务验证token
     * <p>
     * 请求格式：
     * JSON格式：{"token": "xxx.yyy.zzz", "deviceType": 1}
     * <p>
     * 必传参数：
     * - token: JWT token
     * - deviceType: 设备类型（1-4，必传）
     *
     * @param tokenRequest 包含token和设备类型的JSON对象
     * @return 验证结果
     */
    @PostMapping("/validate")
    public WebBaseResponse<String> validateToken(@RequestBody TokenRequest tokenRequest) {
        try {
            // 从请求对象中获取token
            String token = tokenRequest.getToken();

            // 验证token是否为空
            if (StringUtils.isBlank(token)) {
                log.error("Token为空");
                return WebBaseResponse.returnResultError("Token不能为空");
            }

            // 记录token信息用于调试
            log.debug("验证token，长度: {}, 前50字符: {}", token.length(),
                    token.length() > 50 ? token.substring(0, 50) + "..." : token);

            // 验证token格式（JWT格式：xxx.yyy.zzz）
            if (!isValidJwtFormat(token)) {
                log.error("Token格式无效，不是有效的JWT格式");
                return WebBaseResponse.returnResultError("Token格式无效");
            }

            // 获取设备类型（必传）
            ImTerminalType deviceType = tokenRequest.getDeviceType();
            if (deviceType == null) {
                log.error("设备类型参数deviceType未传");
                return WebBaseResponse.returnResultError("设备类型参数deviceType不能为空");
            }

            // 验证设备类型是否在支持范围内
            if (!isValidDeviceType(deviceType)) {
                log.error("不支持的设备类型: {}", deviceType != null ? deviceType.getCode() : "null");
                return WebBaseResponse.returnResultError(getDeviceTypeErrorMessage(deviceType));
            }

            // 使用 JwtTokenService 验证token有效性
            Long userId = jwtTokenService.getUserIdFromToken(token);
            if (userId == null) {
                log.warn("Token无效或已过期，token: {}", token);
                return WebBaseResponse.returnResultError(AnswerCode.TOKEN_INVALID.getMessage());
            }

            // 验证Redis中是否存在该token（额外的安全检查）
            String tokenMd5 = calculateTokenMd5(token);

            // 精确匹配指定设备类型的token：userId:deviceType:tokenMd5
            String keyPattern = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + "*:" + deviceType.getCode() + ":" + tokenMd5;
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(keyPattern);

            String storedUserId = null;
            try {
                for (String key : keys) {
                    storedUserId = redissonUtils.getString(key);
                    if (StringUtils.isNotBlank(storedUserId)) {
                        break; // 找到第一个有效的用户ID
                    }
                }
            } catch (Exception e) {
                log.warn("从Redis获取token信息失败，可能Redis中的数据已损坏，pattern: {}, error: {}", keyPattern, e.getMessage());
                //严格校验
                return WebBaseResponse.returnResultError(AnswerCode.TOKEN_INVALID.getMessage());
            }

            if (StringUtils.isBlank(storedUserId)) {
                log.warn("Token在Redis中不存在或获取失败，可能已被登出或数据损坏，deviceType: {}, tokenMd5: {}", deviceType.getDescription(), tokenMd5);
                return WebBaseResponse.returnResultError(AnswerCode.TOKEN_INVALID.getMessage());
            }

            log.debug("Token验证成功，用户ID: {}", storedUserId);
            return WebBaseResponse.returnResultSuccess(AnswerCode.SUCCESS.getMessage());

        } catch (Exception e) {
            log.error("验证token接口异常，tokenRequest: {}", tokenRequest, e);
            return WebBaseResponse.returnResultError("验证token接口异常: " + e.getMessage());
        }
    }

    /**
     * 计算字符串的MD5哈希值
     */
    private String calculateTokenMd5(String token) {
        return DigestUtil.md5Hex(token);
    }

    /**
     * 验证设备类型是否在支持范围内
     */
    private boolean isValidDeviceType(ImTerminalType deviceType) {
        return deviceType != null && (deviceType == ImTerminalType.ANDROID || deviceType == ImTerminalType.IOS || 
                deviceType == ImTerminalType.MINI_PROGRAM || deviceType == ImTerminalType.WEB);
    }

    /**
     * 获取设备类型支持的说明信息
     */
    private String getDeviceTypeErrorMessage(ImTerminalType deviceType) {
        return "不支持的设备类型，支持的设备类型：1-Android, 2-iOS, 3-小程序, 4-Web";
    }

    /**
     * 从请求参数中获取设备类型（必传）
     */
    private ImTerminalType getDeviceTypeFromParameters(Map<String, String> parameters) {
        String deviceTypeStr = parameters.get("device_type");
        if (StringUtils.isBlank(deviceTypeStr)) {
            log.error("设备类型参数device_type未传");
            throw new RuntimeException("设备类型参数device_type不能为空");
        }
        Integer deviceTypeCode = Integer.valueOf(deviceTypeStr);
        ImTerminalType deviceType = ImTerminalType.fromCode(deviceTypeCode);
        if (deviceType == null || (deviceType != ImTerminalType.ANDROID && deviceType != ImTerminalType.IOS && 
                deviceType != ImTerminalType.MINI_PROGRAM && deviceType != ImTerminalType.WEB)) {
            log.error("无效的设备类型: {}", deviceTypeStr);
            throw new RuntimeException("未知的设备类型，支持的设备类型：1-Android, 2-iOS, 3-小程序, 4-Web");
        }
        return deviceType;
    }

    /**
     * 删除用户指定端的旧token（用于登录时覆盖旧token）
     */
    private void deleteUserDeviceTokens(String userId, ImTerminalType deviceType) {
        try {
            String keyPattern = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + userId + ":" + deviceType.getCode() + ":*";
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(keyPattern);

            if (keys != null) {
                List<String> keyList = new ArrayList<>();
                for (String key : keys) {
                    keyList.add(key);
                }

                if (!keyList.isEmpty()) {
                    redissonUtils.deleteKeys(keyList.toArray(new String[0]));
                    log.debug("删除用户{}端{}的旧token，共删除{}个key", userId, deviceType.getDescription(), keyList.size());
                }
            }
        } catch (Exception e) {
            log.warn("删除用户{}端{}的旧token失败，继续执行新token存储", userId, deviceType.getDescription(), e);
        }
    }

    /**
     * 删除用户指定端的所有token并返回删除数量（用于登出操作）
     */
    private int deleteUserDeviceTokensForLogout(String userId, ImTerminalType deviceType) {
        try {
            String keyPattern = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + userId + ":" + deviceType.getCode() + ":*";
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(keyPattern);

            if (keys != null) {
                List<String> keyList = new ArrayList<>();
                for (String key : keys) {
                    keyList.add(key);
                }

                if (!keyList.isEmpty()) {
                    redissonUtils.deleteKeys(keyList.toArray(new String[0]));
                    log.debug("登出删除用户{}端{}的token，共删除{}个key", userId, deviceType.getDescription(), keyList.size());
                    return keyList.size();
                }
            }
            return 0;
        } catch (Exception e) {
            log.warn("登出删除用户{}端{}的token失败", userId, deviceType.getDescription(), e);
            return 0;
        }
    }

    /**
     * 验证JWT格式是否有效
     */
    private boolean isValidJwtFormat(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }
}
