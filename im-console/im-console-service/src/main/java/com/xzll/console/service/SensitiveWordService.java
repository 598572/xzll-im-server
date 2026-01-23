package com.xzll.console.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.dto.SensitiveWordDTO;
import com.xzll.console.entity.SensitiveWordDO;

import java.util.List;
import java.util.Set;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 敏感词管理服务接口
 */
public interface SensitiveWordService {
    
    /**
     * 分页查询敏感词
     *
     * @param keyword  关键词
     * @param wordType 类型
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 敏感词列表
     */
    Page<SensitiveWordDO> pageSensitiveWords(String keyword, Integer wordType, Integer pageNum, Integer pageSize);
    
    /**
     * 添加敏感词
     *
     * @param dto 敏感词信息
     * @return 是否成功
     */
    boolean addSensitiveWord(SensitiveWordDTO dto);
    
    /**
     * 批量添加敏感词
     *
     * @param words 敏感词列表
     * @param wordType 类型
     * @return 成功添加数量
     */
    int batchAddSensitiveWords(List<String> words, Integer wordType);
    
    /**
     * 更新敏感词
     *
     * @param dto 敏感词信息
     * @return 是否成功
     */
    boolean updateSensitiveWord(SensitiveWordDTO dto);
    
    /**
     * 删除敏感词
     *
     * @param id 敏感词ID
     * @return 是否成功
     */
    boolean deleteSensitiveWord(Long id);
    
    /**
     * 启用/禁用敏感词
     *
     * @param id     敏感词ID
     * @param status 状态
     * @return 是否成功
     */
    boolean toggleSensitiveWord(Long id, Integer status);
    
    /**
     * 获取所有启用的敏感词（用于过滤）
     *
     * @return 敏感词集合
     */
    Set<String> getAllEnabledWords();
    
    /**
     * 检测文本中的敏感词
     *
     * @param text 待检测文本
     * @return 检测到的敏感词列表
     */
    List<String> detectSensitiveWords(String text);
    
    /**
     * 过滤敏感词（替换为*）
     *
     * @param text 原文本
     * @return 过滤后的文本
     */
    String filterSensitiveWords(String text);
}
