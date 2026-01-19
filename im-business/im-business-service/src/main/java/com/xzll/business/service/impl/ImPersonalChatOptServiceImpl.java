package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.business.entity.mysql.ImPersonalChatOpt;
import com.xzll.business.mapper.ImPersonalChatOptMapper;
import com.xzll.business.service.ImPersonalChatOptService;
import com.xzll.common.constant.ImConstant;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;


/**
 * @Author: hzz
 * @Date: 2024/6/3 09:10:48
 * @Description:
 */
@Service
@Slf4j
public class ImPersonalChatOptServiceImpl implements ImPersonalChatOptService {

    @Resource
    private ImPersonalChatOptMapper imPersonalChatOptMapper;

    /**
     * 查询用户对会话的个人操作（分页）
     *
     * @param ao
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @return
     */
    @Override
    public List<ImPersonalChatOpt> findPersonalChatByUserId(ImPersonalChatOpt ao, int currentPage, int pageSize) {
        log.info("查询最近会话列表_入参:{}", JSONUtil.toJsonStr(ao));
        LambdaQueryWrapper<ImPersonalChatOpt> queryWrapper = Wrappers.lambdaQuery(ImPersonalChatOpt.class)
                .and(query -> query.eq(ImPersonalChatOpt::getUserId, ao.getUserId()))
                .and(query -> query.eq(ImPersonalChatOpt::getDelChat, ImConstant.CommonConstant.NO))
                .and(query -> query.eq(ImPersonalChatOpt::getUnShow, ImConstant.CommonConstant.NO))
                .orderByDesc(ImPersonalChatOpt::getToTop);

        Page<ImPersonalChatOpt> page = new Page<>(currentPage, pageSize);
        IPage<ImPersonalChatOpt> resultPage = imPersonalChatOptMapper.selectPage(page, queryWrapper);
        return resultPage.getRecords();
    }

    /**
     * 查询用户对会话的个人操作（不分页）
     *
     * @param ao
     * @param currentPage 当前页码（可为null，表示不分页）
     * @param pageSize 每页大小（可为null，表示不分页）
     * @return
     */
    @Override
    public List<ImPersonalChatOpt> findPersonalChatByUserId(ImPersonalChatOpt ao, Integer currentPage, Integer pageSize) {
        log.info("查询用户所有会话_入参:{}", JSONUtil.toJsonStr(ao));
        
        LambdaQueryWrapper<ImPersonalChatOpt> queryWrapper = Wrappers.lambdaQuery(ImPersonalChatOpt.class)
                .and(query -> query.eq(ImPersonalChatOpt::getUserId, ao.getUserId()))
                .and(query -> query.eq(ImPersonalChatOpt::getDelChat, ImConstant.CommonConstant.NO))
                .and(query -> query.eq(ImPersonalChatOpt::getUnShow, ImConstant.CommonConstant.NO))
                .orderByDesc(ImPersonalChatOpt::getToTop);

        // 如果分页参数为null，则查询所有记录
        if (currentPage == null || pageSize == null) {
            return imPersonalChatOptMapper.selectList(queryWrapper);
        }

        // 否则进行分页查询
        Page<ImPersonalChatOpt> page = new Page<>(currentPage, pageSize);
        IPage<ImPersonalChatOpt> resultPage = imPersonalChatOptMapper.selectPage(page, queryWrapper);
        return resultPage.getRecords();
    }

}
