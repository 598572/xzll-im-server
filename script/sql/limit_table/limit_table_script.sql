-- 目前有两个数据源，数据源创建： create database xzll_im_db_0; create database xzll_im_db_1;
-- 此存储过程，用于批量创建 消息记录表表 ，在分表时使用。

DELIMITER $$

drop procedure IF EXISTS create_msg_record_tables;

CREATE PROCEDURE create_msg_record_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE table_name VARCHAR(255);

    WHILE i < 10 DO
        SET table_name = CONCAT('im_c2c_msg_record_', i);

        SET @create_table_sql = CONCAT('CREATE TABLE IF NOT EXISTS ', table_name, ' (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            from_user_id VARCHAR(100) NOT NULL COMMENT "发送人id",
            to_user_id VARCHAR(100) NOT NULL COMMENT "接收人id",
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT "创建时间",
            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL COMMENT "更新时间",
            msg_id VARCHAR(100) NOT NULL COMMENT "消息唯一id",
            msg_format TINYINT NOT NULL COMMENT "消息格式（1 文本，2 语音 ，3地理位置）",
            msg_content VARCHAR(5000) NOT NULL COMMENT "消息内容",
            msg_create_time BIGINT NOT NULL COMMENT "消息发送时间 精确到毫秒的时间戳",
            retry_count INT DEFAULT 0 NOT NULL,
            msg_status TINYINT NOT NULL COMMENT "消息状态（-1：发送失败，1：到达服务器，2：离线，3：未读，4：已读）",
            withdraw_flag TINYINT DEFAULT 0 NOT NULL COMMENT "撤回标志： 0 未撤回，1 已撤回",
            chat_id VARCHAR(255) NOT NULL COMMENT "会话id：单聊时：（业务类型-会话类型-更小的userId-更大的userId） 群聊时（业务类型-会话类型-发起群聊的userId-时间戳）",
            unique index unique_idx_chat_id_msg_id  (chat_id, msg_id)
        ) COMMENT "消息记录表分表";');

        PREPARE stmt FROM @create_table_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_msg_record_tables();



--  查每个表的记录
SELECT 'im_c2c_msg_record_0' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_0
UNION ALL
SELECT 'im_c2c_msg_record_1' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_1
UNION ALL
SELECT 'im_c2c_msg_record_2' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_2
UNION ALL
SELECT 'im_c2c_msg_record_3' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_3
UNION ALL
SELECT 'im_c2c_msg_record_4' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_4
UNION ALL
SELECT 'im_c2c_msg_record_5' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_5
UNION ALL
SELECT 'im_c2c_msg_record_6' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_6
UNION ALL
SELECT 'im_c2c_msg_record_7' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_7
UNION ALL
SELECT 'im_c2c_msg_record_8' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_8
UNION ALL
SELECT 'im_c2c_msg_record_9' AS table_name, count(1) AS record_count FROM im_c2c_msg_record_9;


-- 查所有
SELECT SUM(count) AS total_count
FROM (
         SELECT count(1) AS count FROM im_c2c_msg_record_0
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_1
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_2
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_3
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_4
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_5
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_6
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_7
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_8
         UNION ALL
         SELECT count(1) AS count FROM im_c2c_msg_record_9
     ) AS combined_counts;



-- 清空表

TRUNCATE TABLE im_c2c_msg_record_0;
TRUNCATE TABLE im_c2c_msg_record_1;
TRUNCATE TABLE im_c2c_msg_record_2;
TRUNCATE TABLE im_c2c_msg_record_3;
TRUNCATE TABLE im_c2c_msg_record_4;
TRUNCATE TABLE im_c2c_msg_record_5;
TRUNCATE TABLE im_c2c_msg_record_6;
TRUNCATE TABLE im_c2c_msg_record_7;
TRUNCATE TABLE im_c2c_msg_record_8;
TRUNCATE TABLE im_c2c_msg_record_9;