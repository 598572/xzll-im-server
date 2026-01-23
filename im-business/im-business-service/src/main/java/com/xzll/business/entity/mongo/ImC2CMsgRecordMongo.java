package com.xzll.business.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.HashIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.Sharded;
import org.springframework.data.mongodb.core.mapping.ShardingStrategy;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: C2C消息记录 MongoDB 实体类
 * 
 * 存储说明：
 * - 集合名称: im_c2c_msg_record
 * - 主键(_id): 使用 chatId_msgId 格式，确保唯一性
 * 
 * 分片设计（重要）：
 * - 分片键: chatId（哈希分片）
 * - 同一会话的消息在同一分片，查询效率高
 * - 分片命令: sh.shardCollection("im_db.im_c2c_msg_record", { "chatId": "hashed" })
 * 
 * 索引设计：
 * - 哈希索引: chatId（用于分片）
 * - 复合索引1: {chatId, msgCreateTime} 用于按会话查询并按时间排序
 * - 复合索引2: {fromUserId, chatId, msgCreateTime} 用于查询某用户发送的消息
 * - 复合索引3: {toUserId, chatId, msgCreateTime} 用于查询某用户接收的消息
 * - 单字段索引: msgId 用于消息ID精确查询
 */
@Data
@Document(collection = "im_c2c_msg_record")
@Sharded(shardKey = { "chatId" }, shardingStrategy = ShardingStrategy.HASH)
@CompoundIndexes({
    // 按会话查询 + 时间排序（最常用的查询场景，分片后单分片内查询）
    @CompoundIndex(name = "idx_chatId_msgCreateTime", def = "{'chatId': 1, 'msgCreateTime': -1}"),
    // 按发送者查询（包含chatId避免跨分片scatter-gather）
    @CompoundIndex(name = "idx_fromUserId_chatId_msgCreateTime", def = "{'fromUserId': 1, 'chatId': 1, 'msgCreateTime': -1}"),
    // 按接收者查询（包含chatId避免跨分片scatter-gather）
    @CompoundIndex(name = "idx_toUserId_chatId_msgCreateTime", def = "{'toUserId': 1, 'chatId': 1, 'msgCreateTime': -1}")
})
public class ImC2CMsgRecordMongo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * MongoDB 文档ID
     * 格式: chatId_msgId（确保唯一性）
     */
    @Id
    private String id;

    /**
     * 会话ID（分片键 - 哈希分片）
     * 格式: bizType-smallerUserId-largerUserId
     * 例如: 1-123456-789012
     * 
     * 重要：该字段为分片键，写入后不可修改！
     */
    @Field("chatId")
    @HashIndexed  // 哈希索引，用于分片
    private String chatId;

    /**
     * 消息唯一ID（雪花算法生成）
     */
    @Field("msgId")
    @Indexed
    private String msgId;

    /**
     * 发送人ID
     */
    @Field("fromUserId")
    private String fromUserId;

    /**
     * 接收人ID
     */
    @Field("toUserId")
    private String toUserId;

    /**
     * 消息格式
     * 1-文本 2-图片 3-语音 4-视频 5-文件
     */
    @Field("msgFormat")
    private Integer msgFormat;

    /**
     * 消息内容
     */
    @Field("msgContent")
    private String msgContent;

    /**
     * 消息创建时间（毫秒时间戳）
     * 用于排序和范围查询
     */
    @Field("msgCreateTime")
    private Long msgCreateTime;

    /**
     * 消息状态
     * 1-服务端已接收 2-已送达 3-已读 4-离线
     */
    @Field("msgStatus")
    private Integer msgStatus;

    /**
     * 重试次数
     */
    @Field("retryCount")
    private Integer retryCount;

    /**
     * 撤回标志
     * 0-未撤回 1-已撤回
     */
    @Field("withdrawFlag")
    private Integer withdrawFlag;

    /**
     * 创建时间（使用 Date 类型，MongoDB 原生支持，避免 JDK 模块反射问题）
     */
    @Field("createTime")
    private Date createTime;

    /**
     * 更新时间
     */
    @Field("updateTime")
    private Date updateTime;

    /**
     * 构建文档ID
     * 格式: chatId_msgId
     */
    public void buildId() {
        this.id = this.chatId + "_" + this.msgId;
    }

    /**
     * 转换为通用的 ImC2CMsgRecord 对象
     * 用于统一返回格式，兼容原有逻辑
     */
    public com.xzll.business.entity.mysql.ImC2CMsgRecord toRecord() {
        com.xzll.business.entity.mysql.ImC2CMsgRecord record = new com.xzll.business.entity.mysql.ImC2CMsgRecord();
        record.setRowkey(this.id);  // MongoDB的id作为rowkey
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
        // Date 转 LocalDateTime
        if (this.createTime != null) {
            record.setCreateTime(this.createTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (this.updateTime != null) {
            record.setUpdateTime(this.updateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return record;
    }
}
