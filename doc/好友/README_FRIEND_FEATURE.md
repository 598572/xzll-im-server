# 好友功能实现说明

## 功能概述

本次实现了完整的好友管理功能，包括：
- 发送好友申请
- 处理好友申请（同意/拒绝）
- 查询好友申请列表
- 查询好友列表  
- 删除好友
- 拉黑/取消拉黑好友

## 数据库表结构

### 1. 好友申请表 (im_friend_request)
```sql
CREATE TABLE `im_friend_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` varchar(100) NOT NULL COMMENT '申请ID',
  `from_user_id` varchar(100) NOT NULL COMMENT '申请人用户ID',
  `to_user_id` varchar(100) NOT NULL COMMENT '被申请人用户ID',
  `request_message` varchar(500) DEFAULT '' COMMENT '申请备注消息',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '申请状态：0-待处理，1-已同意，2-已拒绝，3-已过期',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_from_user_id` (`from_user_id`),
  KEY `idx_to_user_id` (`to_user_id`),
  KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友申请表';
```

### 2. 好友关系表 (im_friend_relation) - 已存在
好友关系表已存在，新增功能会使用此表管理好友关系。

## API接口

### 1. 发送好友申请
- **接口**: `POST /api/friend/request/send`
- **请求参数**:
```json
{
  "fromUserId": "申请人用户ID",
  "toUserId": "被申请人用户ID", 
  "requestMessage": "申请备注消息（可选）"
}
```
- **返回结果**: 申请ID

### 2. 处理好友申请
- **接口**: `POST /api/friend/request/handle`
- **请求参数**:
```json
{
  "requestId": "申请ID",
  "userId": "操作用户ID",
  "handleResult": 1  // 1-同意，2-拒绝
}
```

### 3. 查询好友申请列表
- **接口**: `POST /api/friend/request/list`
- **请求参数**:
```json
{
  "userId": "用户ID",
  "requestType": 2,  // 1-我发出的申请，2-我收到的申请
  "currentPage": 1,
  "pageSize": 20
}
```

### 4. 查询好友列表
- **接口**: `POST /api/friend/list`
- **请求参数**:
```json
{
  "userId": "用户ID",
  "currentPage": 1,
  "pageSize": 20
}
```

### 5. 删除好友
- **接口**: `POST /api/friend/delete`
- **请求参数**: `userId`（用户ID）, `friendId`（好友ID）

### 6. 拉黑/取消拉黑好友
- **接口**: `POST /api/friend/block`
- **请求参数**: `userId`（用户ID）, `friendId`（好友ID）, `blackFlag`（true-拉黑，false-取消拉黑）

## 业务逻辑说明

### 发送好友申请
1. 验证申请人和被申请人不能是同一人
2. 检查是否已经是好友关系
3. 检查是否已有待处理的申请
4. 创建申请记录

### 处理好友申请
1. 验证申请记录存在且状态为待处理
2. 验证操作权限（只有被申请人可以处理）
3. 更新申请状态
4. 如果同意，建立双向好友关系

### 好友关系管理
- 好友关系采用双向存储（A->B 和 B->A 都会创建记录）
- 删除好友时标记删除而非物理删除
- 拉黑操作只影响操作方对被拉黑方的关系

## 代码结构

```
im-business/im-business-service/src/main/java/com/xzll/business/
├── controller/
│   └── ImFriendController.java           # 好友控制器
├── service/
│   ├── ImFriendService.java             # 好友服务接口
│   └── impl/
│       └── ImFriendServiceImpl.java     # 好友服务实现
├── entity/mysql/
│   └── ImFriendRequest.java             # 好友申请实体
└── mapper/
    └── ImFriendRequestMapper.java       # 好友申请Mapper

im-common/src/main/java/com/xzll/common/pojo/
├── request/
│   ├── FriendRequestSendAO.java         # 发送好友申请请求
│   ├── FriendRequestHandleAO.java       # 处理好友申请请求
│   ├── FriendRequestListAO.java         # 查询好友申请列表请求
│   └── FriendListAO.java                # 查询好友列表请求
└── response/
    ├── FriendRequestVO.java             # 好友申请响应
    └── FriendInfoVO.java                # 好友信息响应
```

## 使用说明

1. **执行SQL脚本**: 首先需要在数据库中创建好友申请表，执行 `script/sql/ddl/friend_request_ddl.sql`

2. **发送好友申请**: 客户端调用发送好友申请接口，传入申请人和被申请人ID

3. **处理申请**: 被申请人登录后可以查看收到的好友申请列表，并选择同意或拒绝

4. **查看好友**: 用户可以查看自己的好友列表，进行聊天等操作

5. **好友管理**: 可以删除好友或拉黑好友

## 注意事项

1. 所有接口都有完整的参数校验和异常处理
2. 使用事务保证数据一致性
3. 好友关系是双向的，删除和建立都会同步操作
4. 支持分页查询，避免大数据量问题
5. 所有敏感操作都有详细的日志记录
