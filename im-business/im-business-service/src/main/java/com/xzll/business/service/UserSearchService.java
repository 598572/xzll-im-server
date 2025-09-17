package com.xzll.business.service;

import com.xzll.common.pojo.request.UserSearchAO;
import com.xzll.common.pojo.response.UserSearchVO;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 用户搜索服务接口
 */
public interface UserSearchService {

    /**
     * 搜索用户
     *
     * @param ao 搜索请求参数
     * @return 搜索结果列表
     */
    List<UserSearchVO> searchUsers(UserSearchAO ao);

}
