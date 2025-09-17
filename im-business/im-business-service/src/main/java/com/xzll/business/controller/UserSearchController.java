package com.xzll.business.controller;

import com.xzll.business.service.UserSearchService;
import com.xzll.business.service.SearchSecurityService;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.UserSearchAO;
import com.xzll.common.pojo.response.UserSearchVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 用户搜索控制器
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin
@Slf4j
public class UserSearchController {

    @Resource
    private UserSearchService userSearchService;

    @Resource
    private SearchSecurityService searchSecurityService;

    /**
     * 搜索用户
     */
    @PostMapping("/search")
    public WebBaseResponse<List<UserSearchVO>> searchUsers(@RequestBody UserSearchAO ao) {
        log.info("搜索用户_入参:{}", ao);

        try {
            // 参数校验
            if (ao == null || ao.getKeyword() == null || ao.getCurrentUserId() == null) {
                return WebBaseResponse.returnResultError("搜索关键词和当前用户ID不能为空");
            }

            // 关键词长度校验
            String keyword = ao.getKeyword().trim();
            if (keyword.length() < 2) {
                return WebBaseResponse.returnResultError("搜索关键词至少需要2个字符");
            }
            if (keyword.length() > 50) {
                return WebBaseResponse.returnResultError("搜索关键词不能超过50个字符");
            }

            // 检查搜索频率限制
            if (!searchSecurityService.checkSearchRateLimit(ao.getCurrentUserId())) {
                return WebBaseResponse.returnResultError("搜索过于频繁，请稍后再试");
            }

            // 检查敏感词
            if (searchSecurityService.containsSensitiveWord(keyword)) {
                return WebBaseResponse.returnResultError("搜索关键词包含敏感内容");
            }

            // 设置默认值和限制
            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(10);
            }
            if (ao.getPageSize() > 50) {
                ao.setPageSize(50); // 限制每页最大数量
            }
            if (ao.getSearchType() == null || (ao.getSearchType() != 1 && ao.getSearchType() != 2)) {
                ao.setSearchType(2); // 默认模糊搜索
            }

            // 执行搜索
            List<UserSearchVO> result = userSearchService.searchUsers(ao);
            
            // 记录搜索日志
            searchSecurityService.logSearch(ao.getCurrentUserId(), keyword, result.size());
            
            log.info("搜索用户成功，关键词:{}, 当前用户:{}, 返回{}条记录", 
                    keyword, ao.getCurrentUserId(), result.size());
            
            return WebBaseResponse.returnResultSuccess(result);

        } catch (Exception e) {
            log.error("搜索用户失败，入参:{}", ao, e);
            return WebBaseResponse.returnResultError("搜索用户失败: " + e.getMessage());
        }
    }

}
