package com.xzll.console.entity.es;

import com.xzll.console.entity.ImC2CMsgRecord;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.io.Serializable;

/**
 * C2C消息记录 ES文档实体类
 * 对应ES索引: im_c2c_msg_record
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
@Document(indexName = "im_c2c_msg_record")
@Setting(shards = 3, replicas = 1, refreshInterval = "1s")
public class ImC2CMsgRecordES implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文档ID（使用chatId_msgId作为_id）
     */
    @Id
    private String id;

    /**
     * 会话ID（用于精确查询）
     * 格式: bizType-smallerUserId-largerUserId
     */
    @Field(type = FieldType.Keyword)
    private String chatId;

    /**
     * 消息唯一ID
     */
    @Field(type = FieldType.Keyword)
    private String msgId;

    /**
     * 发送人ID
     */
    @Field(type = FieldType.Keyword)
    private String fromUserId;

    /**
     * 接收人ID
     */
    @Field(type = FieldType.Keyword)
    private String toUserId;

    /**
     * 消息格式
     * 1-文本 2-图片 3-语音 4-视频 5-文件
     */
    @Field(type = FieldType.Integer)
    private Integer msgFormat;

    /**
     * 消息内容（支持全文搜索，中文分词）
     * 使用ik_max_word分词器进行最细粒度分词
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String msgContent;

    /**
     * 消息创建时间（毫秒时间戳）
     */
    @Field(type = FieldType.Long)
    private Long msgCreateTime;

    /**
     * 消息状态
     * 1-服务端已接收 2-已送达 3-已读 4-离线
     */
    @Field(type = FieldType.Integer)
    private Integer msgStatus;

    /**
     * 重试次数
     */
    @Field(type = FieldType.Integer)
    private Integer retryCount;

    /**
     * 撤回标志
     * 0-未撤回 1-已撤回
     */
    @Field(type = FieldType.Integer)
    private Integer withdrawFlag;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Long)
    private Long createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Long)
    private Long updateTime;

    /**
     * 转换为通用的 ImC2CMsgRecord 对象
     */
    public ImC2CMsgRecord toRecord() {
        ImC2CMsgRecord record = new ImC2CMsgRecord();
        record.setRowkey(this.id);
        record.setChatId(this.chatId);
        record.setMsgId(this.msgId);
        record.setFromUserId(this.fromUserId);
        record.setToUserId(this.toUserId);
        record.setMsgFormat(this.msgFormat);
        record.setMsgContent(this.msgContent);
        record.setMsgCreateTime(this.msgCreateTime);
        record.setMsgStatus(this.msgStatus);
        record.setRetryCount(this.retryCount);
        record.setWithdrawFlag(this.withdrawFlag);
        return record;
    }

    /**
     * 从 ImC2CMsgRecord 转换为 ES文档
     */
    public static ImC2CMsgRecordES fromRecord(ImC2CMsgRecord record) {
        ImC2CMsgRecordES es = new ImC2CMsgRecordES();
        es.setId(record.getRowkey());
        es.setChatId(record.getChatId());
        es.setMsgId(record.getMsgId());
        es.setFromUserId(record.getFromUserId());
        es.setToUserId(record.getToUserId());
        es.setMsgFormat(record.getMsgFormat());
        es.setMsgContent(record.getMsgContent());
        es.setMsgCreateTime(record.getMsgCreateTime());
        es.setMsgStatus(record.getMsgStatus());
        es.setRetryCount(record.getRetryCount());
        es.setWithdrawFlag(record.getWithdrawFlag());
        return es;
    }
}
