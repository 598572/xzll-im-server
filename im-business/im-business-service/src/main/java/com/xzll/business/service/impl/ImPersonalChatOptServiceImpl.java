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

import javax.annotation.Resource;
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
     * 查询用户对会话的个人操作
     *
     * @param ao
     * @return
     */
    @Override
    public List<ImPersonalChatOpt> findPersonalChatByUserId(ImPersonalChatOpt ao,int currentPage,int pageSize) {
        log.info("查询最近会话列表_入参:{}", JSONUtil.toJsonStr(ao));
        LambdaQueryWrapper<ImPersonalChatOpt> queryWrapper = Wrappers.lambdaQuery(ImPersonalChatOpt.class)
                //将根据此字段（userId） 分表
                .and(query -> query.eq(ImPersonalChatOpt::getUserId, ao.getUserId()))
                .and(query -> query.eq(ImPersonalChatOpt::getDelFlag, ImConstant.CommonConstant.NO))
                .and(query -> query.eq(ImPersonalChatOpt::getUnShow, ImConstant.CommonConstant.NO))
                .orderByDesc(ImPersonalChatOpt::getToTop, ImPersonalChatOpt::getLastMsgTime);

        Page<ImPersonalChatOpt> page = new Page<>(currentPage, pageSize);
        IPage<ImPersonalChatOpt> resultPage = imPersonalChatOptMapper.selectPage(page, queryWrapper);
        return resultPage.getRecords();
    }

}
