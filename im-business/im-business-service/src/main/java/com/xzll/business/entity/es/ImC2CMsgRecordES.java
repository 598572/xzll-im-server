package com.xzll.business.entity.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息ES存储实体类
 */
@Data
@Document(indexName = "im_c2c_msg_record")
public class ImC2CMsgRecordES {

    @Id
    private String id;

    /**
     * 发送人id
     */
    @Field(type = FieldType.Keyword)
    private String fromUserId;

    /**
     * 接收人id
     */
    @Field(type = FieldType.Keyword)
    private String toUserId;

    /**
     * 消息唯一id
     */
    @Field(type = FieldType.Keyword)
    private String msgId;

    /**
     * 消息格式
     */
    @Field(type = FieldType.Integer)
    private Integer msgFormat;

    /**
     * 消息内容 - 支持全文搜索
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String msgContent;

    /**
     * 消息的发送时间 精确到毫秒
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
     */
    @Field(type = FieldType.Integer)
    private Integer msgStatus;

    /**
     * 撤回标志 0 未撤回 1 已撤回
     */
    @Field(type = FieldType.Integer)
    private Integer withdrawFlag;

    /**
     * 会话id
     */
    @Field(type = FieldType.Keyword)
    private String chatId;

    /**
     * HBase的RowKey
     */
    @Field(type = FieldType.Keyword)
    private String rowkey;

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
     * 格式：chatId + "_" + msgId
     */
    public void buildId() {
        this.id = this.chatId + "_" + this.msgId;
    }
} 