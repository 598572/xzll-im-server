package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 敏感词实体类
 */
@Data
@TableName("im_sensitive_word")
public class SensitiveWordDO {
    
    @TableId(type = IdType.AUTO)
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
    private Integer status;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建人
     */
    private String createBy;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
