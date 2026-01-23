package com.xzll.console.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.AiConfigDO;
import com.xzll.console.entity.AiKnowledgeDO;
import com.xzll.console.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI管理Controller
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai")
@CrossOrigin(origins = "*")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * AI对话（智能客服）
     */
    @PostMapping("/chat")
    public WebBaseResponse<String> chat(@RequestParam String userId,
                               @RequestParam String message) {
        try {
            String response = aiService.chat(userId, message);
            return WebBaseResponse.returnResultSuccess(response);
        } catch (Exception e) {
            log.error("AI对话失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 分页查询AI对话历史
     */
    @GetMapping("/chat/page")
    public WebBaseResponse<IPage<Map<String, Object>>> getChatHistory(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userId) {
        try {
            Page<Map<String, Object>> page = new Page<>(current, size);
            IPage<Map<String, Object>> result = aiService.getChatHistory(page, userId);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询对话历史失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 获取知识库列表
     */
    @GetMapping("/knowledge/page")
    public WebBaseResponse<IPage<AiKnowledgeDO>> getKnowledgePage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category) {
        try {
            Page<AiKnowledgeDO> page = new Page<>(current, size);
            IPage<AiKnowledgeDO> result = aiService.getKnowledgePage(page, category);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询知识库失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 添加知识库
     */
    @PostMapping("/knowledge/add")
    public WebBaseResponse<Void> addKnowledge(@RequestBody AiKnowledgeDO knowledge,
                                      @RequestHeader("X-Admin-Id") String adminId) {
        try {
            knowledge.setCreateBy(adminId);
            aiService.saveKnowledge(knowledge);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("添加知识库失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 更新知识库
     */
    @PutMapping("/knowledge/update")
    public WebBaseResponse<Void> updateKnowledge(@RequestBody AiKnowledgeDO knowledge) {
        try {
            aiService.updateKnowledge(knowledge);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("更新知识库失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/knowledge/{id}")
    public WebBaseResponse<Void> deleteKnowledge(@PathVariable Long id) {
        try {
            aiService.deleteKnowledge(id);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("删除知识库失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 获取AI配置列表
     */
    @GetMapping("/config/list")
    public WebBaseResponse<List<AiConfigDO>> getConfigList() {
        try {
            List<AiConfigDO> list = aiService.getConfigList();
            return WebBaseResponse.returnResultSuccess(list);
        } catch (Exception e) {
            log.error("获取AI配置失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 更新AI配置
     */
    @PutMapping("/config/update")
    public WebBaseResponse<Void> updateConfig(@RequestBody AiConfigDO config,
                                      @RequestHeader("X-Admin-Id") String adminId) {
        try {
            config.setCreateBy(adminId);
            aiService.updateConfig(config);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("更新AI配置失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }
}
