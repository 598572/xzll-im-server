package com.xzll.console.entity.es;

import com.xzll.console.entity.ImC2CMsgRecord;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;

import static com.xzll.common.constant.ImConstant.TableConstant.IM_C2C_MSG_RECORD;

/**
 * C2C消息记录ES实体类
 * 与 im-data-sync 模块的实体保持一致
 * 
 * 字段映射说明:
 * - Keyword类型: 用于精确匹配，不分词（userId、chatId、msgId）
 * - Text类型: 用于全文搜索，支持分词（msgContent）
 * - Long类型: 用于范围查询（msgCreateTime）
 * - Integer类型: 用于状态过滤（msgStatus、msgFormat）
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
@Document(indexName = IM_C2C_MSG_RECORD)
public class ImC2CMsgRecordES implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ES文档ID
     * 格式: chatId_msgId（确保唯一性）
     */
    @Id
    private String id;

    /**
     * HBase的RowKey
     * 用于关联HBase原始数据
     */
    @Field(type = FieldType.Keyword)
    private String rowkey;

    /**
     * 发送方用户ID
     * Keyword类型，支持精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String fromUserId;

    /**
     * 接收方用户ID
     * Keyword类型，支持精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String toUserId;

    /**
     * 消息ID
     * 业务唯一标识
     */
    @Field(type = FieldType.Keyword)
    private String msgId;

    /**
     * 消息格式
     * 1-文本 2-图片 3-语音 4-视频 等
     */
    @Field(type = FieldType.Integer)
    private Integer msgFormat;

    /**
     * 消息内容
     * Text类型，使用ik_max_word分词器，支持中文全文搜索
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String msgContent;

    /**
     * 消息创建时间（时间戳）
     * 用于时间范围查询和排序
     */
    @Field(type = FieldType.Long)
    private Long msgCreateTime;

    /**
     * 重试次数
     */
    @Field(type = FieldType.Integer)
    private Integer retryCount;

    /**
     * 消息状态
     * 0-待发送 1-已发送 2-已送达 3-已读
     */
    @Field(type = FieldType.Integer)
    private Integer msgStatus;

    /**
     * 撤回标志
     * 0-正常 1-已撤回
     */
    @Field(type = FieldType.Integer)
    private Integer withdrawFlag;

    /**
     * 聊天ID（会话ID）
     * Keyword类型，用于按会话查询
     */
    @Field(type = FieldType.Keyword)
    private String chatId;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date)
    private LocalDateTime updateTime;

    /**
     * 构建ES文档ID
     * 格式: chatId_msgId
     */
    public void buildId() {
        this.id = this.chatId + "_" + this.msgId;
    }

    /**
     * 转换为普通实体对象
     * 用于统一返回格式
     */
    public ImC2CMsgRecord toRecord() {
        ImC2CMsgRecord record = new ImC2CMsgRecord();
        record.setRowkey(this.rowkey);
        record.setFromUserId(this.fromUserId);
        record.setToUserId(this.toUserId);
        record.setMsgId(this.msgId);
        record.setMsgFormat(this.msgFormat);
        record.setMsgContent(this.msgContent);
        record.setMsgCreateTime(this.msgCreateTime);
        record.setRetryCount(this.retryCount);
        record.setMsgStatus(this.msgStatus);
        record.setWithdrawFlag(this.withdrawFlag);
        record.setChatId(this.chatId);
        record.setCreateTime(this.createTime);
        record.setUpdateTime(this.updateTime);
        return record;
    }
}
