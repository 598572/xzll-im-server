package com.xzll.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 资源权限配置实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("im_resource_role")
public class ImResourceRole {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 资源路径
     */
    private String resourcePath;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 允许访问的角色，多个角色用逗号分隔
     */
    private String roles;

    /**
     * 资源描述
     */
    private String description;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
