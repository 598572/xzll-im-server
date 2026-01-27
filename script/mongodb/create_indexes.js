/**
 * MongoDB 索引初始化脚本
 * 数据库: im_db
 * 集合: im_c2c_msg_record
 *
 * 执行方式:
 * mongosh mongodb://localhost:27017/im_db create_indexes.js
 *
 * 或者:
 * mongosh
 * use im_db
 * load("create_indexes.js")
 */

// 切换到 im_db 数据库
use im_db

print("==========================================")
print("开始创建 MongoDB 索引...")
print("数据库: im_db")
print("集合: im_c2c_msg_record")
print("==========================================")

// ==========================================
// 消息记录表索引 (im_c2c_msg_record)
// ==========================================

// 0. 哈希索引 - chatId（分片键）
// 用途: 用于 MongoDB 分片集群的哈希分片
print("\n[0/10] 创建哈希索引: idx_chatId_hashed")
try {
    db.im_c2c_msg_record.createIndex(
        { "chatId": "hashed" },
        {
            name: "idx_chatId_hashed",
            background: true
        }
    )
    print("✓ 哈希索引创建成功: idx_chatId_hashed")
} catch (e) {
    print("✗ 哈希索引创建失败: idx_chatId_hashed - " + e.message)
}

// 1. 单字段索引 - msgId
// 用途: 按消息ID精确查询
print("\n[1/10] 创建单字段索引: idx_msgId")
try {
    db.im_c2c_msg_record.createIndex(
        { "msgId": 1 },
        {
            name: "idx_msgId",
            background: true
        }
    )
    print("✓ 单字段索引创建成功: idx_msgId")
} catch (e) {
    print("✗ 单字段索引创建失败: idx_msgId - " + e.message)
}

// 2. 按会话查询 + 时间排序（分片后单分片内查询）
// 用途: 查询某个会话的最新消息
print("\n[2/10] 创建索引: idx_chatId_msgCreateTime")
try {
    db.im_c2c_msg_record.createIndex(
        { "chatId": 1, "msgCreateTime": -1 },
        {
            name: "idx_chatId_msgCreateTime",
            background: true  // 后台创建，不阻塞其他操作
        }
    )
    print("✓ 索引创建成功: idx_chatId_msgCreateTime")
} catch (e) {
    print("✗ 索引创建失败: idx_chatId_msgCreateTime - " + e.message)
}

// 3. 按发送者查询（包含chatId避免跨分片）
// 用途: 查询某个用户在某个会话中发送的消息
print("\n[3/10] 创建索引: idx_fromUserId_chatId_msgCreateTime")
try {
    db.im_c2c_msg_record.createIndex(
        { "fromUserId": 1, "chatId": 1, "msgCreateTime": -1 },
        {
            name: "idx_fromUserId_chatId_msgCreateTime",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_fromUserId_chatId_msgCreateTime")
} catch (e) {
    print("✗ 索引创建失败: idx_fromUserId_chatId_msgCreateTime - " + e.message)
}

// 4. 按接收者查询（包含chatId避免跨分片）
// 用途: 查询某个用户在某个会话中接收的消息
print("\n[4/10] 创建索引: idx_toUserId_chatId_msgCreateTime")
try {
    db.im_c2c_msg_record.createIndex(
        { "toUserId": 1, "chatId": 1, "msgCreateTime": -1 },
        {
            name: "idx_toUserId_chatId_msgCreateTime",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_toUserId_chatId_msgCreateTime")
} catch (e) {
    print("✗ 索引创建失败: idx_toUserId_chatId_msgCreateTime - " + e.message)
}

// 5. 内容模糊搜索（包含chatId避免跨分片）
// 用途: 按消息内容搜索（配合文本索引使用）
print("\n[5/10] 创建索引: idx_chatId_msgContent")
try {
    db.im_c2c_msg_record.createIndex(
        { "chatId": 1, "msgContent": 1 },
        {
            name: "idx_chatId_msgContent",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_chatId_msgContent")
} catch (e) {
    print("✗ 索引创建失败: idx_chatId_msgContent - " + e.message)
}

// ==========================================
// 新增索引：支持高级搜索功能
// ==========================================

// 6. 按消息状态查询（包含分片键chatId，避免跨分片查询）
// 用途: 筛选待发送/已发送/已送达/已读的消息
print("\n[6/10] 创建索引: idx_chatId_msgStatus_msgCreateTime")
try {
    db.im_c2c_msg_record.createIndex(
        { "chatId": 1, "msgStatus": 1, "msgCreateTime": -1 },
        {
            name: "idx_chatId_msgStatus_msgCreateTime",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_chatId_msgStatus_msgCreateTime")
} catch (e) {
    print("✗ 索引创建失败: idx_chatId_msgStatus_msgCreateTime - " + e.message)
}

// 7. 按消息格式查询（包含分片键chatId，避免跨分片查询）
// 用途: 筛选文本/图片/语音/视频/文件/位置类型的消息
print("\n[7/10] 创建索引: idx_chatId_msgFormat_msgCreateTime")
try {
    db.im_c2c_msg_record.createIndex(
        { "chatId": 1, "msgFormat": 1, "msgCreateTime": -1 },
        {
            name: "idx_chatId_msgFormat_msgCreateTime",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_chatId_msgFormat_msgCreateTime")
} catch (e) {
    print("✗ 索引创建失败: idx_chatId_msgFormat_msgCreateTime - " + e.message)
}

// 8. 按撤回标志查询（包含分片键chatId，避免跨分片查询）
// 用途: 筛选正常/已撤回的消息
print("\n[8/10] 创建索引: idx_chatId_withdrawFlag_msgCreateTime")
try {
    db.im_c2c_msg_record.createIndex(
        { "chatId": 1, "withdrawFlag": 1, "msgCreateTime": -1 },
        {
            name: "idx_chatId_withdrawFlag_msgCreateTime",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_chatId_withdrawFlag_msgCreateTime")
} catch (e) {
    print("✗ 索引创建失败: idx_chatId_withdrawFlag_msgCreateTime - " + e.message)
}

// 9. 综合查询索引：支持按状态+格式+撤回标志的多条件查询（包含分片键chatId）
// 用途: 组合查询多个条件，例如查询某个会话中已读的文本消息
print("\n[9/10] 创建索引: idx_chatId_status_format_withdraw")
try {
    db.im_c2c_msg_record.createIndex(
        {
            "chatId": 1,
            "msgStatus": 1,
            "msgFormat": 1,
            "withdrawFlag": 1,
            "msgCreateTime": -1
        },
        {
            name: "idx_chatId_status_format_withdraw",
            background: true
        }
    )
    print("✓ 索引创建成功: idx_chatId_status_format_withdraw")
} catch (e) {
    print("✗ 索引创建失败: idx_chatId_status_format_withdraw - " + e.message)
}

// ==========================================
// 分片配置（仅在分片集群中执行）
// ==========================================
print("\n[10/10] 配置分片（可选）")
try {
    const adminDb = db.getSiblingDB('admin')
    const result = adminDb.runCommand({ listShards: 1 })

    if (result.ok === 1) {
        print("✓ 检测到分片集群环境")

        // 启用数据库分片
        adminDb.runCommand({ enableSharding: "im_db" })
        print("✓ 已启用数据库分片: im_db")

        // 配置集合分片
        try {
            adminDb.runCommand({
                shardCollection: "im_db.im_c2c_msg_record",
                key: { "chatId": "hashed" }
            })
            print("✓ 已配置集合分片: im_db.im_c2c_msg_record (分片键: chatId hashed)")
        } catch (e) {
            if (e.code === 20) {  // AlreadySharded
                print("ℹ 集合已配置分片: im_db.im_c2c_msg_record")
            } else {
                print("⚠ 分片配置警告: " + e.message)
            }
        }
    } else {
        print("ℹ 当前不是分片集群，跳过分片配置")
    }
} catch (e) {
    print("ℹ 跳过分片配置（可能需要管理员权限）: " + e.message)
}

// ==========================================
// 验证索引
// ==========================================

print("\n==========================================")
print("验证索引创建结果...")
print("==========================================\n")

try {
    const indexes = db.im_c2c_msg_record.getIndexes()
    print("当前集合的所有索引 (" + indexes.length + " 个):")
    print("----------------------------------------")

    indexes.forEach((index, idx) => {
        print((idx + 1) + ". " + index.name)
        print("   字段: " + JSON.stringify(index.key))
        if (index.hasOwnProperty("sparse")) {
            print("   稀疏索引: " + index.sparse)
        }
        if (index.hasOwnProperty("unique")) {
            print("   唯一索引: " + index.unique)
        }
        print("")
    })

    // 检查所有索引是否创建成功
    const requiredIndexes = [
        "idx_chatId_hashed",
        "idx_msgId",
        "idx_chatId_msgCreateTime",
        "idx_fromUserId_chatId_msgCreateTime",
        "idx_toUserId_chatId_msgCreateTime",
        "idx_chatId_msgContent",
        "idx_chatId_msgStatus_msgCreateTime",
        "idx_chatId_msgFormat_msgCreateTime",
        "idx_chatId_withdrawFlag_msgCreateTime",
        "idx_chatId_status_format_withdraw"
    ]

    const existingIndexNames = indexes.map(i => i.name)
    const missingIndexes = requiredIndexes.filter(name => !existingIndexNames.includes(name))

    print("\n==========================================")
    if (missingIndexes.length === 0) {
        print("✓✓✓ 所有索引创建成功！ ✓✓✓")
    } else {
        print("⚠️  以下索引创建失败或不存在:")
        missingIndexes.forEach(name => print("   - " + name))
    }
    print("==========================================")

} catch (e) {
    print("✗ 验证索引失败: " + e.message)
}

print("\n索引创建脚本执行完成！")
