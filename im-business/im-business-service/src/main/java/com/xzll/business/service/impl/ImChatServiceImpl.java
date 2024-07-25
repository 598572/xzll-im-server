package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.xzll.business.entity.mysql.ImChat;
import com.xzll.business.mapper.ImChatMapper;
import com.xzll.business.service.ImChatService;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.convert.ConversionService;
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
    private ConversionService conversionService;

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

}
