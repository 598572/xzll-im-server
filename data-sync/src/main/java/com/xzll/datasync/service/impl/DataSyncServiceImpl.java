package com.xzll.datasync.service.impl;

import com.xzll.datasync.entity.ImC2CMsgRecord;
import com.xzll.datasync.service.DataSyncService;
import com.xzll.datasync.service.ESSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 数据同步服务实现类
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Service
@Slf4j
public class DataSyncServiceImpl implements DataSyncService {
    
    @Resource
    private ESSyncService esSyncService;
    
    @Override
    public boolean syncC2CMsgRecord(ImC2CMsgRecord record, String operationType) {
        log.info("开始同步C2C消息记录，操作类型: {}, chatId: {}, msgId: {}", 
                operationType, record.getChatId(), record.getMsgId());
        
        try {
            // 同步到ES
            boolean esResult = esSyncService.syncC2CMsgRecord(record, operationType);
            
            if (esResult) {
                log.info("C2C消息记录同步成功，操作类型: {}, chatId: {}, msgId: {}", 
                        operationType, record.getChatId(), record.getMsgId());
                return true;
            } else {
                log.error("C2C消息记录同步失败，操作类型: {}, chatId: {}, msgId: {}", 
                        operationType, record.getChatId(), record.getMsgId());
                return false;
            }
        } catch (Exception e) {
            log.error("C2C消息记录同步异常，操作类型: {}, chatId: {}, msgId: {}", 
                    operationType, record.getChatId(), record.getMsgId(), e);
            return false;
        }
    }
} 