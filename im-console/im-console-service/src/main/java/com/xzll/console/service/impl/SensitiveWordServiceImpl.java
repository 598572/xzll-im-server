package com.xzll.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.console.dto.SensitiveWordDTO;
import com.xzll.console.entity.SensitiveWordDO;
import com.xzll.console.mapper.SensitiveWordMapper;
import com.xzll.console.service.SensitiveWordService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 敏感词管理服务实现
 * 
 * 使用DFA（确定有限自动机）算法进行敏感词检测，性能高效
 */
@Slf4j
@Service
public class SensitiveWordServiceImpl implements SensitiveWordService {
    
    @Resource
    private SensitiveWordMapper sensitiveWordMapper;
    
    /**
     * 敏感词缓存（DFA树结构）
     */
    private volatile Map<Character, Object> sensitiveWordMap = new ConcurrentHashMap<>();
    
    /**
     * 启用的敏感词集合（用于快速查询）
     */
    private volatile Set<String> enabledWordsCache = ConcurrentHashMap.newKeySet();
    
    private static final String END_FLAG = "isEnd";
    
    @PostConstruct
    public void init() {
        refreshSensitiveWords();
    }
    
    /**
     * 定时刷新敏感词缓存（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void refreshSensitiveWords() {
        log.info("刷新敏感词缓存...");
        try {
            List<String> words = sensitiveWordMapper.selectAllEnabledWords();
            buildDFATree(words);
            enabledWordsCache = new HashSet<>(words);
            log.info("敏感词缓存刷新完成，共{}个敏感词", words.size());
        } catch (Exception e) {
            log.error("刷新敏感词缓存失败", e);
        }
    }
    
    @Override
    public Page<SensitiveWordDO> pageSensitiveWords(String keyword, Integer wordType, Integer pageNum, Integer pageSize) {
        Page<SensitiveWordDO> page = new Page<>(pageNum, pageSize);
        
        LambdaQueryWrapper<SensitiveWordDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SensitiveWordDO::getWord, keyword);
        }
        if (wordType != null) {
            wrapper.eq(SensitiveWordDO::getWordType, wordType);
        }
        wrapper.orderByDesc(SensitiveWordDO::getCreateTime);
        
        return sensitiveWordMapper.selectPage(page, wrapper);
    }
    
    @Override
    public boolean addSensitiveWord(SensitiveWordDTO dto) {
        // 检查是否已存在
        LambdaQueryWrapper<SensitiveWordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveWordDO::getWord, dto.getWord());
        if (sensitiveWordMapper.selectCount(wrapper) > 0) {
            log.warn("敏感词已存在: {}", dto.getWord());
            return false;
        }
        
        SensitiveWordDO entity = new SensitiveWordDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        
        int result = sensitiveWordMapper.insert(entity);
        if (result > 0) {
            // 刷新缓存
            refreshSensitiveWords();
        }
        return result > 0;
    }
    
    @Override
    public int batchAddSensitiveWords(List<String> words, Integer wordType) {
        int successCount = 0;
        for (String word : words) {
            if (!StringUtils.hasText(word)) continue;
            
            SensitiveWordDTO dto = new SensitiveWordDTO();
            dto.setWord(word.trim());
            dto.setWordType(wordType);
            dto.setStatus(1);
            
            if (addSensitiveWord(dto)) {
                successCount++;
            }
        }
        return successCount;
    }
    
    @Override
    public boolean updateSensitiveWord(SensitiveWordDTO dto) {
        SensitiveWordDO entity = new SensitiveWordDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setUpdateTime(LocalDateTime.now());
        
        int result = sensitiveWordMapper.updateById(entity);
        if (result > 0) {
            refreshSensitiveWords();
        }
        return result > 0;
    }
    
    @Override
    public boolean deleteSensitiveWord(Long id) {
        int result = sensitiveWordMapper.deleteById(id);
        if (result > 0) {
            refreshSensitiveWords();
        }
        return result > 0;
    }
    
    @Override
    public boolean toggleSensitiveWord(Long id, Integer status) {
        LambdaUpdateWrapper<SensitiveWordDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SensitiveWordDO::getId, id)
                .set(SensitiveWordDO::getStatus, status)
                .set(SensitiveWordDO::getUpdateTime, LocalDateTime.now());
        
        int result = sensitiveWordMapper.update(null, wrapper);
        if (result > 0) {
            refreshSensitiveWords();
        }
        return result > 0;
    }
    
    @Override
    public Set<String> getAllEnabledWords() {
        return Collections.unmodifiableSet(enabledWordsCache);
    }
    
    @Override
    public List<String> detectSensitiveWords(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        
        Set<String> sensitiveWords = new LinkedHashSet<>();
        char[] chars = text.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            int length = checkSensitiveWord(chars, i);
            if (length > 0) {
                sensitiveWords.add(text.substring(i, i + length));
                i += length - 1;
            }
        }
        
        return new ArrayList<>(sensitiveWords);
    }
    
    @Override
    public String filterSensitiveWords(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        
        StringBuilder result = new StringBuilder(text);
        char[] chars = text.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            int length = checkSensitiveWord(chars, i);
            if (length > 0) {
                // 替换为*
                for (int j = i; j < i + length; j++) {
                    result.setCharAt(j, '*');
                }
                i += length - 1;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 构建DFA树
     */
    @SuppressWarnings("unchecked")
    private void buildDFATree(List<String> words) {
        Map<Character, Object> newMap = new ConcurrentHashMap<>();
        
        for (String word : words) {
            if (!StringUtils.hasText(word)) continue;
            
            Map<Character, Object> currentMap = newMap;
            char[] chars = word.toCharArray();
            
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                Object obj = currentMap.get(c);
                
                if (obj == null) {
                    Map<Character, Object> newNode = new HashMap<>();
                    if (i == chars.length - 1) {
                        newNode.put(END_FLAG.charAt(0), true);
                    }
                    currentMap.put(c, newNode);
                    currentMap = newNode;
                } else {
                    currentMap = (Map<Character, Object>) obj;
                    if (i == chars.length - 1) {
                        currentMap.put(END_FLAG.charAt(0), true);
                    }
                }
            }
        }
        
        this.sensitiveWordMap = newMap;
    }
    
    /**
     * 检查敏感词，返回匹配的长度
     */
    @SuppressWarnings("unchecked")
    private int checkSensitiveWord(char[] chars, int startIndex) {
        Map<Character, Object> currentMap = sensitiveWordMap;
        int matchLength = 0;
        int lastMatchLength = 0;
        
        for (int i = startIndex; i < chars.length; i++) {
            char c = chars[i];
            Object obj = currentMap.get(c);
            
            if (obj == null) {
                break;
            }
            
            matchLength++;
            currentMap = (Map<Character, Object>) obj;
            
            if (currentMap.containsKey(END_FLAG.charAt(0))) {
                lastMatchLength = matchLength;
            }
        }
        
        return lastMatchLength;
    }
}
