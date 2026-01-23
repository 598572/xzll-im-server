package com.xzll.console.vo;

import com.xzll.console.entity.ImC2CMsgRecord;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 消息搜索结果VO
 * 支持分页和统计信息
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
public class MessageSearchResultVO {

    /**
     * 是否成功
     */
    private Boolean success = true;

    /**
     * 消息列表
     */
    private List<ImC2CMsgRecord> data;

    /**
     * 总数量
     */
    private Long total;

    /**
     * 当前页数量
     */
    private Integer count;

    /**
     * 页码
     */
    private Integer pageNum;

    /**
     * 每页数量
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Integer totalPages;

    /**
     * 是否有下一页
     */
    private Boolean hasMore;

    /**
     * 查询耗时（毫秒）
     */
    private Long costMs;

    /**
     * 数据来源（HBASE/ES）
     */
    private String dataSource;

    /**
     * 错误信息
     */
    private String message;

    /**
     * 聚合统计结果
     */
    private Map<String, Object> aggregations;

    /**
     * 创建成功结果
     */
    public static MessageSearchResultVO success(List<ImC2CMsgRecord> data, Long total, Integer pageNum, Integer pageSize) {
        MessageSearchResultVO vo = new MessageSearchResultVO();
        vo.setSuccess(true);
        vo.setData(data);
        vo.setTotal(total);
        vo.setCount(data != null ? data.size() : 0);
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        vo.setTotalPages((int) Math.ceil((double) total / pageSize));
        vo.setHasMore(pageNum * pageSize < total);
        return vo;
    }

    /**
     * 创建失败结果
     */
    public static MessageSearchResultVO fail(String message) {
        MessageSearchResultVO vo = new MessageSearchResultVO();
        vo.setSuccess(false);
        vo.setMessage(message);
        return vo;
    }
}
