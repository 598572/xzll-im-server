package com.xzll.business.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.xzll.business.mapper.ImUserMapper;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.common.pojo.request.BatchUserInfoAO;
import com.xzll.common.pojo.response.BatchUserInfoVO;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

/**
 * 批量用户信息查询控制器
 * 
 * @Author: hzz
 * @Date: 2025/11/20
 * @Description: 支持客户端批量查询未缓存的用户信息，优化会话列表性能
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class BatchUserInfoController {

    @Resource
    private ImUserMapper imUserMapper;

    // 文件服务基础URL配置
    @Value("${minio.file.upload.base-url:http://localhost:8080/im-business/api/file/}")
    private String fileBaseUrl;

    /**
     * 批量查询用户基础信息
     * 
     * @param ao 查询参数
     * @return 用户信息列表
     */
    @PostMapping("/batchInfo")
    public WebBaseResponse<BatchUserInfoVO> batchGetUserInfo(@RequestBody BatchUserInfoAO ao) {
        log.info("批量查询用户信息_入参: {}", JSONUtil.toJsonStr(ao));

        try {
            // 参数校验
            if (ao == null || CollectionUtils.isEmpty(ao.getUserIds())) {
                log.error("批量查询用户信息失败，用户ID列表为空");
                return WebBaseResponse.returnResultError("用户ID列表不能为空");
            }

            if (ao.getUserIds().size() > 50) {
                log.error("批量查询用户信息失败，用户ID数量超过限制: {}", ao.getUserIds().size());
                return WebBaseResponse.returnResultError("用户ID数量不能超过50个");
            }

            // 去重并过滤空值
            List<String> validUserIds = ao.getUserIds().stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());

            if (validUserIds.isEmpty()) {
                log.info("过滤后的用户ID列表为空");
                return WebBaseResponse.returnResultSuccess(new BatchUserInfoVO());
            }

            // 批量查询用户信息
            List<ImUserDO> users = imUserMapper.selectList(
                    Wrappers.lambdaQuery(ImUserDO.class)
                            .in(ImUserDO::getUserId, validUserIds)
            );

            // 组装返回结果
            BatchUserInfoVO result = new BatchUserInfoVO();
            
            if (!CollectionUtils.isEmpty(users)) {
                // 转换为基础信息VO
                List<BatchUserInfoVO.UserBasicInfo> userInfos = users.stream()
                        .map(user -> {
                            BatchUserInfoVO.UserBasicInfo info = new BatchUserInfoVO.UserBasicInfo();
                            info.setUserId(user.getUserId());
                            info.setUserName(user.getUserName());
                            info.setUserFullName(user.getUserFullName());
                            
                            // 转换头像路径为完整访问URL（短链接）
                            if (StringUtils.hasText(user.getHeadImage())) {
                                String shortCode = generateShortCode(user.getHeadImage());
                                String fullAvatarUrl = fileBaseUrl + "s/" + shortCode;
                                info.setHeadImage(fullAvatarUrl);
                                log.debug("批量查询头像URL转换 - 用户ID: {}, 原路径: {}, 短码: {}, 完整URL: {}", 
                                    user.getUserId(), user.getHeadImage(), shortCode, fullAvatarUrl);
                            } else {
                                info.setHeadImage(null);
                            }
                            
                            info.setSex(user.getSex());
                            return info;
                        })
                        .collect(Collectors.toList());
                
                result.setUsers(userInfos);

                // 找出未查询到的用户ID
                Map<String, ImUserDO> userMap = users.stream()
                        .collect(Collectors.toMap(ImUserDO::getUserId, user -> user));
                
                List<String> notFoundIds = validUserIds.stream()
                        .filter(id -> !userMap.containsKey(id))
                        .collect(Collectors.toList());
                
                result.setNotFoundUserIds(notFoundIds);
            } else {
                result.setUsers(Lists.newArrayList());
                result.setNotFoundUserIds(validUserIds);
            }

            log.info("批量查询用户信息成功，查询{}个，找到{}个，未找到{}个", 
                    validUserIds.size(), 
                    result.getUsers() != null ? result.getUsers().size() : 0,
                    result.getNotFoundUserIds() != null ? result.getNotFoundUserIds().size() : 0);

            return WebBaseResponse.returnResultSuccess(result);

        } catch (Exception e) {
            log.error("批量查询用户信息异常", e);
            return WebBaseResponse.returnResultError("批量查询用户信息失败：" + e.getMessage());
        }
    }

    /**
     * 生成短码（Base64编码文件路径）
     */
    private String generateShortCode(String filePath) {
        return java.util.Base64.getEncoder().encodeToString(filePath.getBytes());
    }
}
