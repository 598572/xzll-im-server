package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.xzll.business.entity.mysql.ImChat;
import com.xzll.business.mapper.ImChatMapper;
import com.xzll.business.mapstruct.C2CChatMapping;
import com.xzll.business.service.ImChatService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import com.xzll.common.util.ChatIdUtils;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Objects;


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
    private C2CChatMapping c2CChatMapping;

    /**
     * 保存单聊会话信息
     *
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class,propagation = Propagation.REQUIRED)
    public boolean saveOrUpdateC2CChat(C2CMsgRequestDTO dto) {
        log.info("写入或更新会话信息入参:{}", JSONUtil.toJsonStr(dto));
        String chatId = ChatIdUtils.buildC2CChatId(ImConstant.DEFAULT_BIZ_TYPE, Long.valueOf(dto.getFromUserId()), Long.valueOf(dto.getToUserId()));
        LambdaQueryWrapper<ImChat> eq = Wrappers.lambdaQuery(ImChat.class).eq(ImChat::getChatId, chatId);
        ImChat imChat = imChatMapper.selectOne(eq);
        int row = 0;
        if (Objects.nonNull(imChat)) {
            ImChat imChatUpdate = new ImChat();
            imChatUpdate.setId(imChat.getId());
            imChatUpdate.setLastMsgId(dto.getMsgId());
            imChatUpdate.setLastMsgTime(dto.getMsgCreateTime());
            row = imChatMapper.updateById(imChatUpdate);
        } else {
            ImChat imChatAdd = c2CChatMapping.convertAddC2CImChat(dto);
            row = imChatMapper.insert(imChatAdd);
        }
        log.info("写入或更新会话信息入参:{}", row);
        return row > 0;
    }

}
