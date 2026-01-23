package com.xzll.console.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.dto.SensitiveWordDTO;
import com.xzll.console.entity.SensitiveWordDO;
import com.xzll.console.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 敏感词管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/sensitive-word")
@CrossOrigin(origins = "*")
public class SensitiveWordController {
    
    @Resource
    private SensitiveWordService sensitiveWordService;
    
    /**
     * 分页查询敏感词
     */
    @GetMapping("/page")
    public WebBaseResponse<Page<SensitiveWordDO>> pageSensitiveWords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer wordType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Page<SensitiveWordDO> result = sensitiveWordService.pageSensitiveWords(keyword, wordType, pageNum, pageSize);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询敏感词失败", e);
            return WebBaseResponse.returnResultError("查询敏感词失败");
        }
    }
    
    /**
     * 添加敏感词
     */
    @PostMapping("/add")
    public WebBaseResponse<String> addSensitiveWord(@RequestBody SensitiveWordDTO dto) {
        try {
            boolean success = sensitiveWordService.addSensitiveWord(dto);
            if (success) {
                return WebBaseResponse.returnResultSuccess("添加成功");
            } else {
                return WebBaseResponse.returnResultError("敏感词已存在");
            }
        } catch (Exception e) {
            log.error("添加敏感词失败", e);
            return WebBaseResponse.returnResultError("添加敏感词失败");
        }
    }
    
    /**
     * 批量添加敏感词
     */
    @PostMapping("/batch-add")
    public WebBaseResponse<String> batchAddSensitiveWords(@RequestBody Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            List<String> words = (List<String>) params.get("words");
            Integer wordType = (Integer) params.get("wordType");
            
            int count = sensitiveWordService.batchAddSensitiveWords(words, wordType);
            return WebBaseResponse.returnResultSuccess("成功添加" + count + "个敏感词");
        } catch (Exception e) {
            log.error("批量添加敏感词失败", e);
            return WebBaseResponse.returnResultError("批量添加敏感词失败");
        }
    }
    
    /**
     * 更新敏感词
     */
    @PutMapping("/update")
    public WebBaseResponse<String> updateSensitiveWord(@RequestBody SensitiveWordDTO dto) {
        try {
            boolean success = sensitiveWordService.updateSensitiveWord(dto);
            if (success) {
                return WebBaseResponse.returnResultSuccess("更新成功");
            } else {
                return WebBaseResponse.returnResultError("更新失败");
            }
        } catch (Exception e) {
            log.error("更新敏感词失败", e);
            return WebBaseResponse.returnResultError("更新敏感词失败");
        }
    }
    
    /**
     * 删除敏感词
     */
    @DeleteMapping("/delete/{id}")
    public WebBaseResponse<String> deleteSensitiveWord(@PathVariable Long id) {
        try {
            boolean success = sensitiveWordService.deleteSensitiveWord(id);
            if (success) {
                return WebBaseResponse.returnResultSuccess("删除成功");
            } else {
                return WebBaseResponse.returnResultError("删除失败");
            }
        } catch (Exception e) {
            log.error("删除敏感词失败: id={}", id, e);
            return WebBaseResponse.returnResultError("删除敏感词失败");
        }
    }
    
    /**
     * 启用/禁用敏感词
     */
    @PostMapping("/toggle/{id}")
    public WebBaseResponse<String> toggleSensitiveWord(
            @PathVariable Long id,
            @RequestParam Integer status) {
        try {
            boolean success = sensitiveWordService.toggleSensitiveWord(id, status);
            if (success) {
                return WebBaseResponse.returnResultSuccess(status == 1 ? "已启用" : "已禁用");
            } else {
                return WebBaseResponse.returnResultError("操作失败");
            }
        } catch (Exception e) {
            log.error("切换敏感词状态失败: id={}", id, e);
            return WebBaseResponse.returnResultError("切换敏感词状态失败");
        }
    }
    
    /**
     * 获取所有启用的敏感词
     */
    @GetMapping("/all-enabled")
    public WebBaseResponse<Set<String>> getAllEnabledWords() {
        try {
            Set<String> words = sensitiveWordService.getAllEnabledWords();
            return WebBaseResponse.returnResultSuccess(words);
        } catch (Exception e) {
            log.error("获取敏感词列表失败", e);
            return WebBaseResponse.returnResultError("获取敏感词列表失败");
        }
    }
    
    /**
     * 检测文本中的敏感词
     */
    @PostMapping("/detect")
    public WebBaseResponse<List<String>> detectSensitiveWords(@RequestBody Map<String, String> params) {
        try {
            String text = params.get("text");
            List<String> sensitiveWords = sensitiveWordService.detectSensitiveWords(text);
            return WebBaseResponse.returnResultSuccess(sensitiveWords);
        } catch (Exception e) {
            log.error("检测敏感词失败", e);
            return WebBaseResponse.returnResultError("检测敏感词失败");
        }
    }
    
    /**
     * 过滤敏感词
     */
    @PostMapping("/filter")
    public WebBaseResponse<String> filterSensitiveWords(@RequestBody Map<String, String> params) {
        try {
            String text = params.get("text");
            String filteredText = sensitiveWordService.filterSensitiveWords(text);
            return WebBaseResponse.returnResultSuccess(filteredText);
        } catch (Exception e) {
            log.error("过滤敏感词失败", e);
            return WebBaseResponse.returnResultError("过滤敏感词失败");
        }
    }
}
