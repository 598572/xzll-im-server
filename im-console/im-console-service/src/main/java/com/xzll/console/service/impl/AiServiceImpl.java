package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.entity.AiChatDO;
import com.xzll.console.entity.AiConfigDO;
import com.xzll.console.entity.AiKnowledgeDO;
import com.xzll.console.mapper.AiChatMapper;
import com.xzll.console.mapper.AiConfigMapper;
import com.xzll.console.mapper.AiKnowledgeMapper;
import com.xzll.console.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * AI管理服务实现
 *
 * @author xzll
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private AiChatMapper aiChatMapper;

    @Autowired
    private AiKnowledgeMapper aiKnowledgeMapper;

    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String chat(String userId, String message) {
        // 1. 保存用户消息
        String chatId = UUID.randomUUID().toString().replace("-", "");
        String sessionId = UUID.randomUUID().toString().replace("-", "");

        AiChatDO userMessage = new AiChatDO();
        userMessage.setChatId(chatId);
        userMessage.setUserId(userId);
        userMessage.setSessionId(sessionId);
        userMessage.setMessageType(1); // 用户消息
        userMessage.setContent(message);
        aiChatMapper.insert(userMessage);

        // 2. 从知识库匹配答案
        String answer = matchAnswerFromKnowledge(message);

        // 3. 如果知识库没有匹配，调用AI API（这里先返回模拟回复）
        if (!StringUtils.hasText(answer)) {
            answer = generateAiResponse(message);
        }

        // 4. 保存AI回复
        AiChatDO aiMessage = new AiChatDO();
        aiMessage.setChatId(chatId);
        aiMessage.setUserId(userId);
        aiMessage.setSessionId(sessionId);
        aiMessage.setMessageType(2); // AI回复
        aiMessage.setContent(answer);
        aiChatMapper.insert(aiMessage);

        log.info("AI对话完成: userId={}, message={}, answer={}", userId, message, answer);
        return answer;
    }

    /**
     * 从知识库匹配答案
     */
    private String matchAnswerFromKnowledge(String message) {
        LambdaQueryWrapper<AiKnowledgeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiKnowledgeDO::getStatus, 1); // 启用状态
        wrapper.orderByDesc(AiKnowledgeDO::getPriority);

        List<AiKnowledgeDO> knowledgeList = aiKnowledgeMapper.selectList(wrapper);

        for (AiKnowledgeDO knowledge : knowledgeList) {
            // 匹配问题
            if (knowledge.getQuestion().contains(message) || message.contains(knowledge.getQuestion())) {
                return knowledge.getAnswer();
            }

            // 匹配关键词
            if (StringUtils.hasText(knowledge.getKeywords())) {
                String[] keywords = knowledge.getKeywords().split(",");
                for (String keyword : keywords) {
                    if (message.contains(keyword.trim())) {
                        return knowledge.getAnswer();
                    }
                }
            }
        }

        return null;
    }

    /**
     * 调用AI API生成回复（模拟实现）
     * TODO: 集成真实的AI API（OpenAI/文心一言等）
     */
    private String generateAiResponse(String message) {
        // 这里是模拟实现，实际应该调用真实的AI API
        // 可以从配置中获取API密钥等配置

        Map<String, String> configMap = getConfigMap();
        String apiKey = configMap.get("ai.api.key");
        String model = configMap.get("ai.model");

        log.info("调用AI API: model={}, apiKey={}", model, apiKey != null ? "已配置" : "未配置");

        // 模拟AI回复
        return "您好，我是智能客服助手。您的问题：「" + message + "」我已经收到了，但由于尚未配置真实AI API，目前只能回复此模拟消息。请在AI配置中心配置API密钥后使用。";
    }

    @Override
    public IPage<Map<String, Object>> getChatHistory(Page<Map<String, Object>> page, String userId) {
        // 使用自定义SQL查询或直接查询实体后转换
        LambdaQueryWrapper<AiChatDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(userId)) {
            wrapper.eq(AiChatDO::getUserId, userId);
        }
        wrapper.orderByDesc(AiChatDO::getCreateTime);

        // 创建正确类型的Page
        Page<AiChatDO> chatPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<AiChatDO> result = aiChatMapper.selectPage(chatPage, wrapper);

        // 转换为Map返回
        IPage<Map<String, Object>> resultPage = new Page<>(page.getCurrent(), page.getSize(), result.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (AiChatDO chat : result.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("chatId", chat.getChatId());
            map.put("userId", chat.getUserId());
            map.put("sessionId", chat.getSessionId());
            map.put("messageType", chat.getMessageType());
            map.put("content", chat.getContent());
            map.put("createTime", chat.getCreateTime());
            records.add(map);
        }
        resultPage.setRecords(records);

        return resultPage;
    }

    @Override
    public IPage<AiKnowledgeDO> getKnowledgePage(Page<AiKnowledgeDO> page, String category) {
        LambdaQueryWrapper<AiKnowledgeDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(AiKnowledgeDO::getCategory, category);
        }
        wrapper.orderByDesc(AiKnowledgeDO::getPriority);
        return aiKnowledgeMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveKnowledge(AiKnowledgeDO knowledge) {
        if (knowledge.getStatus() == null) {
            knowledge.setStatus(1); // 默认启用
        }
        if (knowledge.getPriority() == null) {
            knowledge.setPriority(0);
        }
        aiKnowledgeMapper.insert(knowledge);
        log.info("知识库添加成功: question={}, category={}", knowledge.getQuestion(), knowledge.getCategory());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(AiKnowledgeDO knowledge) {
        aiKnowledgeMapper.updateById(knowledge);
        log.info("知识库更新成功: id={}", knowledge.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long id) {
        aiKnowledgeMapper.deleteById(id);
        log.info("知识库删除成功: id={}", id);
    }

    @Override
    public List<AiConfigDO> getConfigList() {
        LambdaQueryWrapper<AiConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfigDO::getStatus, 1); // 启用状态
        return aiConfigMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(AiConfigDO config) {
        LambdaQueryWrapper<AiConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiConfigDO::getConfigKey, config.getConfigKey());

        AiConfigDO existingConfig = aiConfigMapper.selectOne(wrapper);
        if (existingConfig != null) {
            existingConfig.setConfigValue(config.getConfigValue());
            aiConfigMapper.updateById(existingConfig);
            log.info("AI配置更新成功: configKey={}", config.getConfigKey());
        } else {
            aiConfigMapper.insert(config);
            log.info("AI配置添加成功: configKey={}", config.getConfigKey());
        }
    }

    /**
     * 获取配置Map
     */
    private Map<String, String> getConfigMap() {
        List<AiConfigDO> configList = getConfigList();
        Map<String, String> configMap = new HashMap<>();
        for (AiConfigDO config : configList) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }
        return configMap;
    }
}
