package com.xzll.auth.controller;

import com.xzll.auth.config.nacos.Oauth2Config;
import com.xzll.auth.constant.AuthConstant;
import com.xzll.auth.domain.Oauth2TokenDto;
import com.xzll.auth.domain.TokenRequest;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

import org.springframework.security.oauth2.provider.endpoint.TokenEndpoint;
import org.springframework.security.oauth2.provider.token.TokenStore;
import java.util.zip.CRC32;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;


import javax.annotation.Resource;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;


/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 登录 获取token
 */
@RestController
@Slf4j
@RequestMapping("/oauth")
public class AuthController {

    @Resource
    private TokenEndpoint tokenEndpoint;

    @Resource
    private TokenStore tokenStore;

    @Autowired
    @Qualifier(value = "myStringRedisTemplate")
    private RedisTemplate<String,String> myStringRedisTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier(value = "secondaryRedisTemplate")
    private RedisTemplate<String, Object> secondaryRedisTemplate;


    @Resource
    private Oauth2Config oauth2Config;
    
    @Resource
    private UserDetailsService userDetailsService;

    /**
     * OAuth2登录认证接口
     * 
     * 登录流程：
     * 1. 验证请求参数（用户名、密码、grant_type等）
     * 2. 调用OAuth2认证端点验证用户凭据
     * 3. 生成JWT访问令牌和刷新令牌
     * 4. 将token和用户ID存储到Redis中
     * 5. 返回token信息给客户端
     * 
     * @param principal 当前认证主体（通常为空，因为这是登录接口）
     * @param parameters 登录参数，包含：username、password、grant_type、client_id等
     * @return 包含访问令牌的响应对象
     */
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public WebBaseResponse<Oauth2TokenDto> postAccessToken(Principal principal, @RequestParam Map<String, String> parameters) {
        try {
            // 第一步：验证请求参数
            // 检查参数是否为空，确保客户端传递了必要的登录信息
            if (parameters == null || parameters.isEmpty()) {
                log.error("登录参数为空");
                return WebBaseResponse.returnResultError("登录参数不能为空");
            }
            
            // 第二步：调用OAuth2认证端点，验证用户凭据并生成访问令牌
            // tokenEndpoint.postAccessToken() 会：
            // 1. 验证用户名和密码是否正确
            // 2. 检查用户状态（是否启用、锁定等）
            // 3. 生成JWT访问令牌和刷新令牌
            // 4. 如果认证失败，会抛出AuthenticationException异常
            OAuth2AccessToken oAuth2AccessToken = tokenEndpoint.postAccessToken(principal, parameters).getBody();
            
            // 第三步：验证获取到的访问令牌
            // 正常情况下不应该为空，如果为空说明认证过程中出现了问题
            if (oAuth2AccessToken == null) {
                log.error("获取OAuth2AccessToken失败");
                return WebBaseResponse.returnResultError("获取访问令牌失败");
            }
            
            // 第四步：构建返回给客户端的token信息
            // 包含：访问令牌、刷新令牌、过期时间、token前缀等
            Oauth2TokenDto oauth2TokenDto = Oauth2TokenDto.builder()
                    .token(oAuth2AccessToken.getValue())  // JWT访问令牌
                    .refreshToken(oAuth2AccessToken.getRefreshToken() != null ? oAuth2AccessToken.getRefreshToken().getValue() : null)  // 刷新令牌
                    .expiresIn(oauth2Config.getTokenTimeOut())  // 过期时间（秒）
                    .tokenHead(AuthConstant.TOKEN_PREFIX).build();  // Token前缀 "Bearer "

            // 第五步：将token和用户ID存储到Redis中
            // 用于后续的token验证和用户会话管理
            saveTokenToRedis(oAuth2AccessToken, oauth2TokenDto);
            
            // 第六步：返回成功响应
            return WebBaseResponse.returnResultSuccess(oauth2TokenDto);
            
        } catch (HttpRequestMethodNotSupportedException e) {
            // 处理HTTP方法不支持异常（如GET请求访问POST接口）
            log.error("不支持的HTTP请求方法", e);
            return WebBaseResponse.returnResultError("不支持的请求方法");
        } catch (Exception e) {
            // 处理其他所有异常（包括认证失败、参数错误等）
            log.error("登录认证异常", e);
            return WebBaseResponse.returnResultError("登录失败：" + e.getMessage());
        }
    }
    
    /**
     * 保存token到Redis缓存
     * 
     * 作用：
     * 1. 将JWT token和用户ID的映射关系存储到Redis
     * 2. 设置token的过期时间，与JWT的过期时间保持一致
     * 3. 用于后续的token验证和用户会话管理
     * 
     * @param oAuth2AccessToken OAuth2访问令牌对象，包含JWT token和额外信息
     * @param oauth2TokenDto 返回给客户端的token信息对象
     */
    private void saveTokenToRedis(OAuth2AccessToken oAuth2AccessToken, Oauth2TokenDto oauth2TokenDto) {
        // 第一步：验证token是否为空
        if (StringUtils.isBlank(oauth2TokenDto.getToken())) {
            log.warn("Token为空，跳过Redis存储");
            return;
        }
        
        // 第二步：从JWT token中提取用户ID
        // JWT token的payload中包含了我们在JwtTokenEnhancer中设置的额外信息
        Map<String, Object> additionalInfo = oAuth2AccessToken.getAdditionalInformation();
        log.debug("Token additional information: {}", additionalInfo);
        
        // 第三步：验证JWT中是否包含用户ID信息
        if (additionalInfo == null || !additionalInfo.containsKey(AuthConstant.JWT_USER_ID_KEY)) {
            log.error("Token中缺少用户ID信息，additionalInfo: {}", additionalInfo);
            throw new RuntimeException("Token中缺少用户ID信息");
        }
        
        // 第四步：提取用户ID
        Object idObj = additionalInfo.get(AuthConstant.JWT_USER_ID_KEY);
        String uid = idObj != null ? idObj.toString() : StringUtils.EMPTY;
        log.debug("从Token中获取到的用户ID: {}", uid);
        
        // 第五步：验证用户ID是否为空
        if (StringUtils.isBlank(uid)) {
            log.error("用户ID为空");
            throw new RuntimeException("用户ID不能为空");
        }
        
        // 第六步：将token和用户ID存储到Redis
        try {
            // 使用CRC32对token进行哈希，避免Redis中存储过长的token
            long tokenCrc32 = calculateTokenCrc32(oauth2TokenDto.getToken());
            // 构建Redis key：USER_TOKEN_KEY + CRC32(token)
            String redisKey = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + tokenCrc32;
            // 存储映射关系：key -> 用户ID，并设置过期时间
            myStringRedisTemplate.opsForValue().set(redisKey, uid, oauth2Config.getTokenTimeOut());
            log.debug("Token已保存到Redis，key: {}, tokenCrc32: {}", redisKey, tokenCrc32);
        } catch (Exception e) {
            log.error("保存Token到Redis失败", e);
            throw new RuntimeException("保存Token失败");
        }
    }

    /**
     * Token刷新接口
     * 
     * 刷新流程：
     * 1. 验证刷新令牌是否有效
     * 2. 使用OAuth2标准流程生成新的访问令牌和刷新令牌
     * 3. 更新Redis中的token信息
     * 4. 返回新的token信息给客户端
     * 
     * @param refreshToken 刷新令牌
     * @return 包含新访问令牌的响应对象
     */
    @PostMapping("/refresh")
    public WebBaseResponse<Oauth2TokenDto> refreshToken(@RequestParam String refreshToken) {
        try {
            // 第一步：验证刷新令牌参数
            if (StringUtils.isBlank(refreshToken)) {
                log.error("刷新令牌为空");
                return WebBaseResponse.returnResultError("刷新令牌不能为空");
            }
            
            // 第二步：使用OAuth2标准流程刷新token
            // 构建刷新token的请求参数
            Map<String, String> parameters = new HashMap<>();
            parameters.put("grant_type", "refresh_token");
            parameters.put("refresh_token", refreshToken);
            parameters.put("client_id", oauth2Config.getClientId());
            parameters.put("client_secret", oauth2Config.getPassword());
            
            // 创建客户端认证信息
            // 这是解决 "There is no client authentication" 错误的关键
            // 需要创建一个包含客户端ID和密码的认证对象
            UsernamePasswordAuthenticationToken clientAuth = new UsernamePasswordAuthenticationToken(
                    oauth2Config.getClientId(), 
                    oauth2Config.getPassword(), 
                    null
            );
            
            // 调用OAuth2的token端点进行刷新，传入客户端认证信息
            OAuth2AccessToken newAccessToken = tokenEndpoint.postAccessToken(clientAuth, parameters).getBody();
            if (newAccessToken == null) {
                log.error("刷新token失败，无法获取新的访问令牌");
                return WebBaseResponse.returnResultError("刷新token失败");
            }
            
            // 第三步：构建返回给客户端的token信息
            Oauth2TokenDto oauth2TokenDto = Oauth2TokenDto.builder()
                    .token(newAccessToken.getValue())  // 新的JWT访问令牌
                    .refreshToken(newAccessToken.getRefreshToken() != null ? newAccessToken.getRefreshToken().getValue() : null)  // 新的刷新令牌
                    .expiresIn(oauth2Config.getTokenTimeOut())  // 过期时间（秒）
                    .tokenHead(AuthConstant.TOKEN_PREFIX).build();  // Token前缀 "Bearer "

            // 第四步：更新Redis中的token信息
            saveTokenToRedis(newAccessToken, oauth2TokenDto);
            
            // 第五步：返回成功响应
            log.info("Token刷新成功，用户ID: {}", getUserIdFromToken(newAccessToken));
            return WebBaseResponse.returnResultSuccess(oauth2TokenDto);
            
        } catch (Exception e) {
            log.error("Token刷新异常，refreshToken: {}", refreshToken, e);
            return WebBaseResponse.returnResultError("Token刷新失败：" + e.getMessage());
        }
    }
    
    /**
     * 从token中获取用户ID
     */
    private String getUserIdFromToken(OAuth2AccessToken token) {
        try {
            Map<String, Object> additionalInfo = token.getAdditionalInformation();
            if (additionalInfo != null && additionalInfo.containsKey(AuthConstant.JWT_USER_ID_KEY)) {
                return additionalInfo.get(AuthConstant.JWT_USER_ID_KEY).toString();
            }
            return null;
        } catch (Exception e) {
            log.error("从token中获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 用户登出接口
     * 
     * 网关已做认证，此接口从Redis中删除指定的token
     * 
     * @param token 要登出的token（网关已验证其有效性）
     * @return 登出结果
     */
    @PostMapping("/logout")
    public WebBaseResponse<String> logout(@RequestParam String token) {
        try {
            // 网关已做认证，这里只需要从Redis中删除token
            if (StringUtils.isBlank(token)) {
                log.error("登出token为空");
                return WebBaseResponse.returnResultError("Token不能为空");
            }
            
            // 第三步：从Redis中删除token
            long tokenCrc32 = calculateTokenCrc32(token);
            String redisKey = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + tokenCrc32;
            Boolean deleted = myStringRedisTemplate.delete(redisKey);
            log.info("从Redis删除token，key: {}, 结果: {}", redisKey, deleted);
            
            return WebBaseResponse.returnResultSuccess("登出成功");
            
        } catch (Exception e) {
            log.error("用户登出异常", e);
            return WebBaseResponse.returnResultError("登出失败：" + e.getMessage());
        }
    }
    


    /**
     * 验证 jwt Token
     * 
     * 网关已做认证，此接口主要用于不走网关的服务验证token
     * 
     * 支持两种格式：
     * 1. JSON格式：{"token": "xxx.yyy.zzz"}
     * 2. 直接字符串：xxx.yyy.zzz
     * 
     * @param tokenRequest 包含token的JSON对象
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
            
            // 验证token有效性
            OAuth2AccessToken accessToken = tokenStore.readAccessToken(token);
            if (accessToken == null || accessToken.isExpired()) {
                log.warn("Token无效或已过期，token: {}", token);
                return WebBaseResponse.returnResultError(AnswerCode.TOKEN_INVALID.getMessage());
            }
            
            // 验证Redis中是否存在该token（额外的安全检查）
            long tokenCrc32 = calculateTokenCrc32(token);
            String redisKey = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + tokenCrc32;
            String storedUserId = null;
            try {
                storedUserId = myStringRedisTemplate.opsForValue().get(redisKey);
            } catch (Exception e) {
                log.warn("从Redis获取token信息失败，可能Redis中的数据已损坏，key: {}, error: {}", redisKey, e.getMessage());
                // Redis数据可能损坏，但不影响token验证，继续执行
            }
            
            if (StringUtils.isBlank(storedUserId)) {
                log.warn("Token在Redis中不存在或获取失败，可能已被登出或数据损坏，tokenCrc32: {}", tokenCrc32);
                // 注意：这里可以选择是否返回错误，或者继续验证token本身的有效性
                // 暂时继续验证，因为JWT本身是自包含的
            }
            
            log.debug("Token验证成功，用户ID: {}", storedUserId);
            return WebBaseResponse.returnResultSuccess(AnswerCode.SUCCESS.getMessage());
            
        } catch (Exception e) {
            log.error("验证token接口异常，tokenRequest: {}", tokenRequest, e);
            return WebBaseResponse.returnResultError("验证token接口异常: " + e.getMessage());
        }
    }
    
    /**
     * 测试Redis存储格式
     */
    @PostMapping("/test-redis")
    public WebBaseResponse<String> testRedisStorage() {
        try {
            String testKey = "test:key:123";
            String testValue = "test_value_456";
            
            // 存储测试数据
            myStringRedisTemplate.opsForValue().set(testKey, testValue, 60);
            
            // 读取测试数据
            String retrievedValue = myStringRedisTemplate.opsForValue().get(testKey);
            
            // 删除测试数据
            myStringRedisTemplate.delete(testKey);


            
            return WebBaseResponse.returnResultSuccess(
                String.format("测试成功！存储值: %s, 读取值: %s, 是否相等: %s", 
                    testValue, retrievedValue, testValue.equals(retrievedValue))
            );
        } catch (Exception e) {
            log.error("Redis测试失败", e);
            return WebBaseResponse.returnResultError("Redis测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试三种RedisTemplate的存储效果对比
     */
    @PostMapping("/test-redis-comparison")
    public WebBaseResponse<String> testRedisComparison() {
        try {
            StringBuilder result = new StringBuilder();
            String testKey = "testcomparison:";
            String testValue = "test_value_789";
            
            // 测试1: myStringRedisTemplate (自定义的字符串RedisTemplate)
            try {
                String key1 = testKey + "1";
                myStringRedisTemplate.opsForValue().set(key1, testValue, 60);
                String value1 = myStringRedisTemplate.opsForValue().get(key1);
                myStringRedisTemplate.delete(key1);
                result.append("1. myStringRedisTemplate: 成功 - 存储值: ").append(testValue)
                      .append(", 读取值: ").append(value1)
                      .append(", 是否相等: ").append(testValue.equals(value1)).append("\n");
            } catch (Exception e) {
                result.append("1. myStringRedisTemplate: 失败 - ").append(e.getMessage()).append("\n");
            }
            
            // 测试2: redisTemplate (默认的RedisTemplate)
            try {
                String key2 = testKey + "2";
                redisTemplate.opsForValue().set(key2, testValue, 60);
                Object value2 = redisTemplate.opsForValue().get(key2);
                redisTemplate.delete(key2);
                result.append("2. redisTemplate: 成功 - 存储值: ").append(testValue)
                      .append(", 读取值: ").append(value2)
                      .append(", 是否相等: ").append(testValue.equals(value2)).append("\n");
            } catch (Exception e) {
                result.append("2. redisTemplate: 失败 - ").append(e.getMessage()).append("\n");
            }
            
            // 测试3: stringRedisTemplate (Spring Boot的StringRedisTemplate)
            try {
                String key3 = testKey + "3";
                stringRedisTemplate.opsForValue().set(key3, testValue, 60);
                String value3 = stringRedisTemplate.opsForValue().get(key3);
                stringRedisTemplate.delete(key3);
                result.append("3. stringRedisTemplate: 成功 - 存储值: ").append(testValue)
                      .append(", 读取值: ").append(value3)
                      .append(", 是否相等: ").append(testValue.equals(value3)).append("\n");
            } catch (Exception e) {
                result.append("3. stringRedisTemplate: 失败 - ").append(e.getMessage()).append("\n");
            }
            
            // 测试4: 测试数字类型存储
            try {
                String key4 = testKey + "4";
                String numberValue = "123456";
                myStringRedisTemplate.opsForValue().set(key4, numberValue, 60);
                String retrievedNumber = myStringRedisTemplate.opsForValue().get(key4);
                myStringRedisTemplate.delete(key4);
                result.append("4. 数字存储测试: 成功 - 存储值: ").append(numberValue)
                      .append(", 读取值: ").append(retrievedNumber)
                      .append(", 是否相等: ").append(numberValue.equals(retrievedNumber)).append("\n");
            } catch (Exception e) {
                result.append("4. 数字存储测试: 失败 - ").append(e.getMessage()).append("\n");
            }
            
            // 测试3: 测试特殊字符存储
            try {
                String key5 = testKey + "5";
                String specialValue = "test@#$%^&*()_+-=[]{}|;':\",./<>?";
                myStringRedisTemplate.opsForValue().set(key5, specialValue, 60);
                String retrievedSpecial = myStringRedisTemplate.opsForValue().get(key5);
                myStringRedisTemplate.delete(key5);
                result.append("5. 特殊字符测试: 成功 - 存储值: ").append(specialValue)
                      .append(", 读取值: ").append(retrievedSpecial)
                      .append(", 是否相等: ").append(specialValue.equals(retrievedSpecial)).append("\n");
            } catch (Exception e) {
                result.append("5. 特殊字符测试: 失败 - ").append(e.getMessage()).append("\n");
            }

            // 测试3: 测试特殊字符存储
            try {
                String key5 = testKey + "6";
                String specialValue = "kkmkxajsnaxkjxnaacbdha查生产搬家哈删除表";
                secondaryRedisTemplate.opsForValue().set(key5, specialValue, 60);
                String retrievedSpecial = myStringRedisTemplate.opsForValue().get(key5);
//                secondaryRedisTemplate.delete(key5);
                result.append("6. 特殊字符测试: 成功 - 存储值: ").append(specialValue)
                        .append(", 读取值: ").append(retrievedSpecial)
                        .append(", 是否相等: ").append(specialValue.equals(retrievedSpecial)).append("\n");
            } catch (Exception e) {
                result.append("6. 特殊字符测试: 失败 - ").append(e.getMessage()).append("\n");
            }
            
            return WebBaseResponse.returnResultSuccess(result.toString());
        } catch (Exception e) {
            log.error("Redis对比测试失败", e);
            return WebBaseResponse.returnResultError("Redis对比测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试token格式和编码
     */
    @PostMapping("/validate/test")
    public WebBaseResponse<String> testTokenFormat(@RequestBody TokenRequest tokenRequest) {
        try {
            String token = tokenRequest.getToken();
            
            if (StringUtils.isBlank(token)) {
                return WebBaseResponse.returnResultError("Token为空");
            }
            
            // 检查基本信息
            StringBuilder result = new StringBuilder();
            result.append("Token长度: ").append(token.length()).append("\n");
            result.append("Token前50字符: ").append(token.length() > 50 ? token.substring(0, 50) + "..." : token).append("\n");
            
            // 检查字符编码
            if (isValidUtf8String(token)) {
                result.append("UTF-8编码: 有效\n");
            } else {
                result.append("UTF-8编码: 无效\n");
            }
            
            // 检查JWT格式
            if (isValidJwtFormat(token)) {
                result.append("JWT格式: 有效\n");
            } else {
                result.append("JWT格式: 无效\n");
            }
            
            return WebBaseResponse.returnResultSuccess(result.toString());
            
        } catch (Exception e) {
            log.error("测试token格式异常", e);
            return WebBaseResponse.returnResultError("测试异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证 jwt Token（直接字符串格式）
     * 
     * 网关已做认证，此接口主要用于不走网关的服务验证token
     * 
     * @param token 直接的JWT token字符串
     * @return 验证结果
     */
    @PostMapping("/validate/string")
    public WebBaseResponse<String> validateTokenString(@RequestBody String token) {
        try {
            // 验证token是否为空
            if (StringUtils.isBlank(token)) {
                log.error("Token为空");
                return WebBaseResponse.returnResultError("Token不能为空");
            }
            
            // 验证token格式（JWT格式：xxx.yyy.zzz）
            if (!isValidJwtFormat(token)) {
                log.error("Token格式无效，不是有效的JWT格式");
                return WebBaseResponse.returnResultError("Token格式无效");
            }
            
            // 验证token有效性
            OAuth2AccessToken accessToken = tokenStore.readAccessToken(token);
            if (accessToken == null || accessToken.isExpired()) {
                log.warn("Token无效或已过期，token: {}", token);
                return WebBaseResponse.returnResultError(AnswerCode.TOKEN_INVALID.getMessage());
            }
            
            // 验证Redis中是否存在该token（额外的安全检查）
            long tokenCrc32 = calculateTokenCrc32(token);
            String redisKey = ImConstant.RedisKeyConstant.USER_TOKEN_KEY + tokenCrc32;
            String storedUserId = myStringRedisTemplate.opsForValue().get(redisKey);
            if (StringUtils.isBlank(storedUserId)) {
                log.warn("Token在Redis中不存在，可能已被登出，token: {}", token);
                return WebBaseResponse.returnResultError("Token已失效");
            }
            
            log.debug("Token验证成功，用户ID: {}", storedUserId);
            return WebBaseResponse.returnResultSuccess(AnswerCode.SUCCESS.getMessage());
            
        } catch (Exception e) {
            log.error("验证token接口异常，token: {}", token, e);
            return WebBaseResponse.returnResultError("验证token接口异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证JWT token格式
     * 
     * @param token JWT token
     * @return 是否为有效的JWT格式
     */
    private boolean isValidJwtFormat(String token) {
        try {
            // JWT格式：header.payload.signature
            if (StringUtils.isBlank(token)) {
                return false;
            }
            
            // 检查token是否包含无效字符
            if (!isValidUtf8String(token)) {
                log.warn("Token包含无效字符");
                return false;
            }
            
            // 检查是否包含两个点（JWT标准格式）
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Token格式错误，期望3个部分，实际: {}", parts.length);
                return false;
            }
            
            // 检查每个部分是否为空
            for (int i = 0; i < parts.length; i++) {
                if (StringUtils.isBlank(parts[i])) {
                    log.warn("Token第{}部分为空", i + 1);
                    return false;
                }
            }
            
            // 检查token长度（JWT通常不会太短）
            if (token.length() < 50) {
                log.warn("Token长度过短: {}", token.length());
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("验证JWT格式异常", e);
            return false;
        }
    }
    
    /**
     * 计算token的CRC32哈希值
     * 
     * @param token JWT token
     * @return CRC32哈希值
     */
    private long calculateTokenCrc32(String token) {
        CRC32 crc32 = new CRC32();
        crc32.update(token.getBytes());
        return crc32.getValue();
    }

    /**
     * 验证字符串是否为有效的UTF-8编码
     * 
     * @param str 要验证的字符串
     * @return 是否为有效的UTF-8字符串
     */
    private boolean isValidUtf8String(String str) {
        try {
            if (str == null) {
                return false;
            }
            
            // 检查字符串长度
            if (str.length() > 10000) {
                log.warn("Token长度过长: {}", str.length());
                return false;
            }
            
            // 检查是否包含无效字符
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                // 检查是否为有效的UTF-16字符
                if (c >= 0xD800 && c <= 0xDFFF) {
                    // 这是UTF-16代理对，需要特殊处理
                    if (i + 1 < str.length()) {
                        char next = str.charAt(i + 1);
                        if ((c >= 0xD800 && c <= 0xDBFF) && (next >= 0xDC00 && next <= 0xDFFF)) {
                            i++; // 跳过下一个字符
                            continue;
                        }
                    }
                    log.warn("Token包含无效的UTF-16代理对，位置: {}", i);
                    return false;
                }
                
                // 检查是否为控制字符（除了制表符、换行符、回车符）
                if (c < 32 && c != 9 && c != 10 && c != 13) {
                    log.warn("Token包含控制字符，位置: {}, 字符: {}", i, (int)c);
                    return false;
                }
            }
            
            // 尝试转换为字节数组再转回字符串，验证编码
            byte[] bytes = str.getBytes("UTF-8");
            String decoded = new String(bytes, "UTF-8");
            if (!str.equals(decoded)) {
                log.warn("Token编码转换失败");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("验证UTF-8字符串异常", e);
            return false;
        }
    }


    /**
     * 填充im服务地址 此逻辑移到网关层 在响应时做处理
     *
     * @param oauth2TokenDto
     */
//    private void fillImServerAddress(Oauth2TokenDto oauth2TokenDto) {
//        Map<Object, Object> entries = redisTemplate.opsForHash().entries(NETTY_IP_PORT);
//        Assert.isTrue(!CollectionUtils.isEmpty(entries), "无长连接服务可用");
//
//        List<ImServerAddressDTO> addressDTOS = entries.entrySet().stream().map(x -> {
//            ImServerAddressDTO imServerAddressDTO = new ImServerAddressDTO();
//            imServerAddressDTO.setIp(x.getKey().toString());
//            imServerAddressDTO.setPort(Integer.valueOf(x.getValue().toString()));
//            return imServerAddressDTO;
//        }).collect(Collectors.toList());
//
//        //todo 策略模式 支持多种负载算法 ：随机 hash 轮询
//
//        //【随机】边界： 左闭 右开
//        int randomIndex = ThreadLocalRandom.current().nextInt(0, addressDTOS.size());
//        log.info("随机策略_randomIndex值:{}", randomIndex);
//        ImServerAddressDTO randomResult = addressDTOS.get(randomIndex);
//
//        //【轮询】
//        int seatCount = addressDTOS.size();
//        long current = redisTemplate.opsForValue().increment(IMSERVER_ROUND_COUNTER_KEY) - 1;
//        long index = current % seatCount;
//
//        ImServerAddressDTO roundResult = addressDTOS.get((int) index);
//        log.info("轮询策略_当前轮询值:{},index结果:{},胜出的ip端口:{}", current, index, JSONUtil.toJsonStr(roundResult));
//        BeanUtil.copyProperties(randomResult, oauth2TokenDto);
//    }
}
