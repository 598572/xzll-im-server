package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.google.common.collect.Lists;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.entity.mysql.ImChat;
import com.xzll.business.entity.mysql.ImPersonalChatOpt;
import com.xzll.business.mapper.ImChatMapper;
import com.xzll.business.service.ImChatService;
import com.xzll.business.service.ImPersonalChatOptService;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.LastChatListAO;
import com.xzll.common.pojo.response.LastChatListVO;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;



import org.apache.commons.lang3.StringUtils;





/**
 * @Author: hzz
 * @Date: 2024/6/3 09:10:48
 * @Description:
 */
@Service
@Slf4j
public class ImChatServiceImpl implements ImChatService {

    @Resource
    private ImChatMapper imChatMapper;
    @Resource
    private ConversionService conversionService;
    @Resource
    private ImPersonalChatOptService imPersonalChatOptService;
    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;

    /**
     * 保存单聊会话信息
     *
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean saveOrUpdateC2CChat(C2CSendMsgAO dto) {
        log.info("写入或更新会话信息入参:{}", JSONUtil.toJsonStr(dto));
        LambdaQueryWrapper<ImChat> eq = Wrappers.lambdaQuery(ImChat.class).eq(ImChat::getChatId, dto.getChatId());
        ImChat imChat = imChatMapper.selectOne(eq);
        int row = 0;
        if (Objects.nonNull(imChat)) {
            ImChat imChatUpdate = new ImChat();
            imChatUpdate.setLastMsgId(dto.getMsgId());
            imChatUpdate.setLastMsgTime(dto.getMsgCreateTime());
            LambdaUpdateWrapper<ImChat> updateParam = Wrappers.lambdaUpdate(ImChat.class).eq(ImChat::getChatId, dto.getChatId());
            row = imChatMapper.update(imChatUpdate, updateParam);
        } else {
            ImChat imChatAdd = conversionService.convert(dto, ImChat.class);
            row = imChatMapper.insert(imChatAdd);
        }
        log.info("写入或更新会话信息结果:{}", row);
        return row > 0;
    }


    /**
     * 此接口将做成http接口 不通过长连接，因为那样的话 可能存在离线消息先到达客户端，最近会话列表后达到客户端，不太好处理。做成http 的，登录后 http请求最近会话，在进行长连接建立
     *
     * 查询最近会话列表 ， 此功能 应该是在首次登陆和下拉刷新，时调用，后边的会话列表变动 将依靠推送的消息，按最新消息进行倒叙排序
     *
     * 注： 本来可以一个表关联直接得出数据，但是为了后期可能的分库分表 暂时不做表关联，而是分开查询之后组装数据返回
     *
     * @param ao
     * @return
     */
    @Override
    public List<LastChatListVO> findLastChatList(LastChatListAO ao) {
        log.info("查询最近会话列表_入参:{}", JSONUtil.toJsonStr(ao));
        List<LastChatListVO> lastChatListVOs;

        //先根据userId过滤此用户的 隐藏/删除 的会话，并根据置顶和最新消息时间排序
        ImPersonalChatOpt imPersonalChatOpt = new ImPersonalChatOpt();
        imPersonalChatOpt.setUserId(ao.getUserId());
        List<ImPersonalChatOpt> personalChatByUserId = imPersonalChatOptService.findPersonalChatByUserId(imPersonalChatOpt, ao.getCurrentPage(), ao.getPageSize());

        if (CollectionUtils.isEmpty(personalChatByUserId)) {
            return Lists.newArrayList();
        }

        //in 查询
        LambdaQueryWrapper<ImChat> queryWrapper = Wrappers.lambdaQuery(ImChat.class)
                .and(query -> query.in(ImChat::getChatId, personalChatByUserId.stream().map(ImPersonalChatOpt::getChatId).collect(Collectors.toList())))
                .orderByDesc(ImChat::getLastMsgTime);
        List<ImChat> imChats = imChatMapper.selectList(queryWrapper);

        // 构建批量查询参数
        Map<String, String> chatMsgIds = imChats.stream()
                .filter(chat -> StringUtils.isNotBlank(chat.getLastMsgId()))
                .collect(Collectors.toMap(
                        ImChat::getChatId,
                        ImChat::getLastMsgId,
                        (existing, replacement) -> existing // 处理重复key
                ));

        // 批量查询最后一条消息内容&格式 - 完全由HBase服务层处理
        Map<String, ImC2CMsgRecord> lastMsgMap = imC2CMsgRecordHBaseService.batchGetLastMessages(chatMsgIds);

        // 组装返回结果
        lastChatListVOs = imChats.stream().map(x -> {
            LastChatListVO convert = conversionService.convert(x, LastChatListVO.class);

            // 从批量查询结果中获取最后一条消息的详细内容
            ImC2CMsgRecord lastMsg = lastMsgMap.get(x.getChatId());
            if (lastMsg != null) {
                convert.setLastMessageContent(lastMsg.getMsgContent());
                convert.setLastMsgFormat(lastMsg.getMsgFormat());
            }

            return convert;
        }).collect(Collectors.toList());

        // 将置顶的会话排到头部
        lastChatListVOs = sortChatListWithTopPriority(lastChatListVOs, personalChatByUserId);

        log.debug("查询最近会话列表_出参:{}", JSONUtil.toJsonStr(lastChatListVOs));
        return lastChatListVOs;
    }



    /**
     * 根据置顶状态排序会话列表
     */
    private List<LastChatListVO> sortChatListWithTopPriority(List<LastChatListVO> chatList,
                                                        List<ImPersonalChatOpt> personalChatOpts) {
        // 创建置顶会话的映射
        Map<String, ImPersonalChatOpt> topChatMap = personalChatOpts.stream()
            .filter(opt -> opt.getToTop() != null && opt.getToTop() == 1)
            .collect(Collectors.toMap(ImPersonalChatOpt::getChatId, Function.identity()));

        return chatList.stream()
            .sorted((a, b) -> {
                boolean aIsTop = topChatMap.containsKey(a.getChatId());
                boolean bIsTop = topChatMap.containsKey(b.getChatId());

                if (aIsTop && !bIsTop) return -1;
                if (!aIsTop && bIsTop) return 1;

                // 都是置顶或都不是置顶，按时间排序
                return Long.compare(b.getLastMsgTime(), a.getLastMsgTime());
            })
            .collect(Collectors.toList());
    }
}
