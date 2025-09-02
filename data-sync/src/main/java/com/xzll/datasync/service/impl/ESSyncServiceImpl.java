package com.xzll.datasync.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.xzll.datasync.entity.ImC2CMsgRecord;
import com.xzll.datasync.entity.ImC2CMsgRecordES;
import com.xzll.datasync.service.ESSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * ES同步服务实现类
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Service
@Slf4j
public class ESSyncServiceImpl implements ESSyncService {
    
    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;
    
    @Override
    public boolean syncC2CMsgRecord(ImC2CMsgRecord record, String operationType) {
        try {
            ImC2CMsgRecordES esRecord = new ImC2CMsgRecordES();
            BeanUtil.copyProperties(record, esRecord);
            
            // 构建ES文档ID
            esRecord.buildId();
            
            // 保存到ES
            ImC2CMsgRecordES savedRecord = elasticsearchRestTemplate.save(esRecord);
            log.info("C2C消息同步到ES成功，操作类型: {}, ES ID: {}, chatId: {}, msgId: {}", 
                    operationType, savedRecord.getId(), record.getChatId(), record.getMsgId());
            return true;
        } catch (Exception e) {
            log.error("C2C消息同步到ES失败，操作类型: {}, chatId: {}, msgId: {}", 
                    operationType, record.getChatId(), record.getMsgId(), e);
            return false;
        }
    }
} 