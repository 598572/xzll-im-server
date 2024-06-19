package com.xzll.business.mapstruct;

import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;



/**
 * @Author: hzz
 * @Date: 2024/6/3 11:41:39
 * @Description:
 */
@Mapper(componentModel = "spring")
public interface C2CMsgMapping {

    /**
     *
     * @param dto
     * @return
     */
    @Mapping(target = "msgFormat", defaultValue = "1")
    @Mapping(target = "msgType", expression = "java(dto.getMsgType().getFirstLevelMsgType())")
    ImC2CMsgRecord convertC2CMsgRecord(C2CSendMsgAO dto);


}
