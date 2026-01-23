package com.xzll.console.entity.mongo;

import com.xzll.console.entity.ImC2CMsgRecord;
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
 * @Description: C2C消息记录 MongoDB 实体类（im-console 查询用）
 * 
 * 存储说明：
 * - 集合名称: im_c2c_msg_record
 * - 主键(_id): chatId_msgId 格式
 * 
 * 分片设计（重要）：
 * - 分片键: chatId（哈希分片）
 * - 查询时尽量带上 chatId，避免跨分片 scatter-gather
 * - 分片命令: sh.shardCollection("im_db.im_c2c_msg_record", { "chatId": "hashed" })
 */
@Data
@Document(collection = "im_c2c_msg_record")
@Sharded(shardKey = { "chatId" }, shardingStrategy = ShardingStrategy.HASH)
@CompoundIndexes({
    // 按会话查询 + 时间排序（分片后单分片内查询）
    @CompoundIndex(name = "idx_chatId_msgCreateTime", def = "{'chatId': 1, 'msgCreateTime': -1}"),
    // 按发送者查询（包含chatId避免跨分片）
    @CompoundIndex(name = "idx_fromUserId_chatId_msgCreateTime", def = "{'fromUserId': 1, 'chatId': 1, 'msgCreateTime': -1}"),
    // 按接收者查询（包含chatId避免跨分片）
    @CompoundIndex(name = "idx_toUserId_chatId_msgCreateTime", def = "{'toUserId': 1, 'chatId': 1, 'msgCreateTime': -1}"),
    // 内容模糊搜索（包含chatId避免跨分片，需配合文本索引使用）
    @CompoundIndex(name = "idx_chatId_msgContent", def = "{'chatId': 1, 'msgContent': 1}")
})
public class ImC2CMsgRecordMongo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /**
     * 会话ID（分片键 - 哈希分片）
     * 查询时带上此字段可避免跨分片查询
     */
    @Field("chatId")
    @HashIndexed  // 哈希索引，用于分片
    private String chatId;

    @Field("msgId")
    @Indexed
    private String msgId;

    @Field("fromUserId")
    private String fromUserId;

    @Field("toUserId")
    private String toUserId;

    @Field("msgFormat")
    private Integer msgFormat;

    @Field("msgContent")
    private String msgContent;

    @Field("msgCreateTime")
    private Long msgCreateTime;

    @Field("msgStatus")
    private Integer msgStatus;

    @Field("retryCount")
    private Integer retryCount;

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
