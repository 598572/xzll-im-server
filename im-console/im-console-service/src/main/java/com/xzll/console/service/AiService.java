package com.xzll.console.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.AiConfigDO;
import com.xzll.console.entity.AiKnowledgeDO;

import java.util.List;
import java.util.Map;

/**
 * AI管理服务
 */
public interface AiService {
    String chat(String userId, String message);
    IPage<Map<String, Object>> getChatHistory(Page<Map<String, Object>> page, String userId);
    IPage<AiKnowledgeDO> getKnowledgePage(Page<AiKnowledgeDO> page, String category);
    void saveKnowledge(AiKnowledgeDO knowledge);
    void updateKnowledge(AiKnowledgeDO knowledge);
    void deleteKnowledge(Long id);
    List<AiConfigDO> getConfigList();
    void updateConfig(AiConfigDO config);
}
