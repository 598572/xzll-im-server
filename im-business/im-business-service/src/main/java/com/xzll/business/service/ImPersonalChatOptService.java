package com.xzll.business.service;

import com.xzll.business.entity.mysql.ImPersonalChatOpt;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/16 15:58:34
 * @Description:
 */
public interface ImPersonalChatOptService {

    /**
     * 查询个人的会话信息（分页）
     *
     * @param ao
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @return
     */
    public List<ImPersonalChatOpt> findPersonalChatByUserId(ImPersonalChatOpt ao, int currentPage, int pageSize);

    /**
     * 查询个人的会话信息（不分页，查询所有）
     *
     * @param ao
     * @return
     */
    public List<ImPersonalChatOpt> findPersonalChatByUserId(ImPersonalChatOpt ao, Integer currentPage, Integer pageSize);
}
