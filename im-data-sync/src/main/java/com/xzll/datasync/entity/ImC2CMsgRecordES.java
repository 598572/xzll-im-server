package com.xzll.datasync.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C2C消息记录ES实体类
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
@Document(indexName = "im_c2c_msg_record")
public class ImC2CMsgRecordES implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * ES文档ID
     */
    @Id
    private String id;
    
    /**
     * RowKey
     */
    @Field(type = FieldType.Keyword)
    private String rowkey;
    
    /**
     * 发送方用户ID
     */
    @Field(type = FieldType.Keyword)
    private String fromUserId;
    
    /**
     * 接收方用户ID
     */
    @Field(type = FieldType.Keyword)
    private String toUserId;
    
    /**
     * 消息ID
     */
    @Field(type = FieldType.Keyword)
    private String msgId;
    
    /**
     * 消息格式
     */
    @Field(type = FieldType.Integer)
    private Integer msgFormat;
    
    /**
     * 消息内容
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String msgContent;
    
    /**
     * 消息创建时间
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
     * 撤回标志
     */
    @Field(type = FieldType.Integer)
    private Integer withdrawFlag;
    
    /**
     * 聊天ID
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
     */
    public void buildId() {
        this.id = this.chatId + "_" + this.msgId;
    }
} 