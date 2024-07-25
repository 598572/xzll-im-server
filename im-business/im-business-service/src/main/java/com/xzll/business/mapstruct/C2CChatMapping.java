package com.xzll.business.mapstruct;

import com.xzll.business.entity.mysql.ImChat;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.util.ChatIdUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;


/**
 * @Author: hzz
 * @Date: 2024/6/3 11:41:39
 * @Description:
 */
@Mapper(componentModel = "spring",imports = ChatIdUtils.class)
public interface C2CChatMapping extends Converter<C2CSendMsgAO, ImChat> {

    /**
     * 单聊消息转会话 do类
     *
     * @param dto
     * @return
     */
    @Override
    @Mapping(target = "chatType",constant = "1")
    @Mapping(target = "lastMsgId", source = "msgId")
    @Mapping(target = "lastMsgTime", source = "msgCreateTime")
    ImChat convert(C2CSendMsgAO dto);


}
