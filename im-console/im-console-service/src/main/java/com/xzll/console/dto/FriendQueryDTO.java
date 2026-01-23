package com.xzll.console.dto;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友查询DTO
 */
@Data
public class FriendQueryDTO {
    
    /**
     * 页码
     */
    private Integer pageNum = 1;
    
    /**
     * 每页大小
     */
    private Integer pageSize = 10;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 好友ID
     */
    private String friendId;
    
    /**
     * 是否拉黑: 0未拉黑, 1已拉黑
     */
    private Integer blackFlag;
    
    /**
     * 关键词（用户ID或好友ID）
     */
    private String keyword;
}
