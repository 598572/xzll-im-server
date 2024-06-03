package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.xzll.business.entity.ImC2CMsgRecord;
import com.xzll.business.mapper.ImC2CMsgRecordMapper;
import com.xzll.business.mapstruct.C2CMsgMapping;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/6/3 09:10:48
 * @Description:
 */
@Service
@Slf4j
public class ImC2CMsgRecordServiceImpl implements ImC2CMsgRecordService {

    @Resource
    private ImC2CMsgRecordMapper imC2CMsgRecordMapper;
    @Resource
    private C2CMsgMapping c2CMsgMapping;

    @Override
    public void saveC2CMsg(C2CMsgRequestDTO dto) {
        log.info("保存单聊消息入参:{}", JSONUtil.toJsonStr(dto));
        ImC2CMsgRecord imC2CMsgRecord = c2CMsgMapping.convertC2CMsgRecord(dto);
        int row = imC2CMsgRecordMapper.insert(imC2CMsgRecord);
        log.info("保存单聊消息结果:{}",row);
    }
}
