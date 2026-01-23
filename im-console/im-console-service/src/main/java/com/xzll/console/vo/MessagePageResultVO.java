package com.xzll.console.vo;

import com.xzll.console.entity.ImC2CMsgRecord;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 消息分页查询结果VO
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
public class MessagePageResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息列表
     */
    private List<ImC2CMsgRecord> data;

    /**
     * 当前页数量
     */
    private Integer count;

    /**
     * 每页限制
     */
    private Integer limit;

    /**
     * 是否有更多数据
     */
    private Boolean hasMore;

    /**
     * 下一页的RowKey（用于游标分页）
     */
    private String nextRowKey;

    /**
     * 会话ID（可选）
     */
    private String chatId;

    /**
     * 数据来源（HBASE/ES）
     */
    private String dataSource;
}
