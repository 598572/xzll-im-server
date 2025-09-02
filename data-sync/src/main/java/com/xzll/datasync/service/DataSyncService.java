package com.xzll.datasync.service;

import com.xzll.datasync.entity.ImC2CMsgRecord;

/**
 * 数据同步服务接口
 * @Author: hzz
 * @Date: 2024/12/20
 */
public interface DataSyncService {
    
    /**
     * 同步C2C消息记录
     * @param record 消息记录
     * @param operationType 操作类型
     * @return 是否成功
     */
    boolean syncC2CMsgRecord(ImC2CMsgRecord record, String operationType);
} 