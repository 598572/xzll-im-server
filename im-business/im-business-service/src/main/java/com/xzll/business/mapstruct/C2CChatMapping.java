package com.xzll.business.mapstruct;

import com.xzll.business.entity.mysql.ImChat;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.util.ChatIdUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


/**
 * @Author: hzz
 * @Date: 2024/6/3 11:41:39
 * @Description:
 */
@Mapper(componentModel = "spring",imports = ChatIdUtils.class)
public interface C2CChatMapping {

    /**
     * 单聊消息转会话 do类
     *
     * @param dto
     * @return
     */
    @Mapping(target = "chatType",constant = "1")
    @Mapping(target = "lastMsgId", source = "msgId")
    @Mapping(target = "lastMsgTime", source = "msgCreateTime")
    @Mapping(target = "chatId", expression = "java(ChatIdUtils.buildC2CChatId(null,Long.valueOf(dto.getFromUserId()),Long.valueOf(dto.getToUserId())))")
    ImChat convertAddC2CImChat(C2CSendMsgAO dto);


}
