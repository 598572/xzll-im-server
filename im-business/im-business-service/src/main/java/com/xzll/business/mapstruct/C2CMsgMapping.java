package com.xzll.business.mapstruct;

import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.core.convert.converter.Converter;


/**
 * @Author: hzz
 * @Date: 2024/6/3 11:41:39
 * @Description:
 */
@Mapper(componentModel = "spring")
public interface C2CMsgMapping  extends Converter<C2CSendMsgAO, ImC2CMsgRecord> {

    /**
     *
     * @param dto
     * @return
     */
    @Override
    @Mapping(target = "msgFormat", defaultValue = "1")
    ImC2CMsgRecord convert(C2CSendMsgAO dto);


}
