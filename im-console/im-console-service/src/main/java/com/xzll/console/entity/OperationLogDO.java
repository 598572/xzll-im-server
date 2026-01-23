package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 *
 * @author xzll
 */
@Data
@TableName("im_admin_operation_log")
public class OperationLogDO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 管理员ID
     */
    private String adminId;

    /**
     * 管理员名称
     */
    private String adminName;

    /**
     * 操作类型：USER_DISABLE/USER_ENABLE/USER_KICK/WORD_ADD等
     */
    private String operationType;

    /**
     * 操作目标类型：USER/MESSAGE/WORD等
     */
    private String targetType;

    /**
     * 操作目标ID
     */
    private String targetId;

    /**
     * 操作描述
     */
    private String operationDesc;

    /**
     * 请求IP
     */
    private String requestIp;

    /**
     * 请求参数（JSON）
     */
    private String requestParams;

    /**
     * 响应结果：SUCCESS/FAIL
     */
    private String responseResult;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
