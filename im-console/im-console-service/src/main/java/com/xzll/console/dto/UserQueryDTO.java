package com.xzll.console.dto;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户查询请求DTO
 */
@Data
public class UserQueryDTO {
    
    /**
     * 搜索关键词（用户ID/用户名/手机号）
     */
    private String keyword;
    
    /**
     * 用户ID精确查询
     */
    private String userId;
    
    /**
     * 性别筛选
     */
    private Integer sex;
    
    /**
     * 终端类型筛选
     */
    private Integer terminalType;
    
    /**
     * 注册开始时间
     */
    private String registerTimeStart;
    
    /**
     * 注册结束时间
     */
    private String registerTimeEnd;
    
    /**
     * 页码
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量
     */
    private Integer pageSize = 10;
}
