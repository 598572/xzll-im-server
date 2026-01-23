package com.xzll.console.dto;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 敏感词请求DTO
 */
@Data
public class SensitiveWordDTO {
    
    /**
     * ID（更新时使用）
     */
    private Long id;
    
    /**
     * 敏感词
     */
    private String word;
    
    /**
     * 敏感词类型：1-政治敏感，2-色情，3-暴力，4-广告，5-其他
     */
    private Integer wordType;
    
    /**
     * 替换词（可选）
     */
    private String replaceWord;
    
    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status = 1;
    
    /**
     * 备注
     */
    private String remark;
}
