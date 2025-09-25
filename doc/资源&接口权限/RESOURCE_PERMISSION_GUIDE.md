# 资源权限配置数据库化指南

## 🎯 功能说明

已成功将资源权限配置从代码硬编码迁移到数据库管理，实现了权限配置的动态化和可视化管理。

## ✅ 已完成的功能

### 1. 数据库表结构
- 创建了 `im_resource_role` 表存储资源权限配置
- 支持资源路径、角色、描述、状态等字段
- 包含完整的审计字段（创建时间、更新时间）

### 2. 实体和Mapper
- `ImResourceRole` - 资源权限实体类
- `ImResourceRoleMapper` - MyBatis Plus Mapper接口
- 支持查询启用的资源权限配置

### 3. 服务层改造
- `ResourceServiceImpl` 从数据库动态加载配置
- 支持配置刷新功能
- 提供兜底的默认配置机制
- 自动同步配置到Redis

### 4. 管理接口
- 完整的CRUD操作接口
- 支持启用/禁用资源权限
- 支持配置实时刷新
- 提供当前配置查看功能

## 🚀 使用指南

### 1. 数据库初始化

执行SQL脚本创建表和初始数据：

```sql
-- 执行脚本
source /path/to/script/sql/ddl/im_resource_role.sql
```

### 2. 启动服务

启动 `im-auth` 服务，服务会自动：
- 从数据库加载权限配置
- 将配置同步到Redis
- 如果数据库无数据，使用默认配置

### 3. 管理接口使用

#### 查询所有资源权限配置
```bash
curl -X GET "http://localhost:8080/im-auth/api/resource/list"
```

#### 查看当前内存中的配置
```bash
curl -X GET "http://localhost:8080/im-auth/api/resource/current"
```

#### 新增资源权限配置
```bash
curl -X POST "http://localhost:8080/im-auth/api/resource/add" \
  -H "Content-Type: application/json" \
  -d '{
    "resourcePath": "/im-business/api/message/send",
    "resourceName": "发送消息",
    "roles": "ADMIN,USER",
    "description": "发送IM消息接口",
    "status": 1
  }'
```

#### 更新资源权限配置
```bash
curl -X POST "http://localhost:8080/im-auth/api/resource/update" \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "resourcePath": "/im-business/api/message/send",
    "resourceName": "发送消息",
    "roles": "ADMIN,USER,GUEST",
    "description": "发送IM消息接口（更新）",
    "status": 1
  }'
```

#### 启用/禁用资源权限
```bash
curl -X POST "http://localhost:8080/im-auth/api/resource/toggle/1"
```

#### 删除资源权限配置
```bash
curl -X POST "http://localhost:8080/im-auth/api/resource/delete/1"
```

#### 刷新配置（重新从数据库加载）
```bash
curl -X POST "http://localhost:8080/im-auth/api/resource/refresh"
```

## 📊 数据库表结构

```sql
CREATE TABLE `im_resource_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `resource_path` varchar(255) NOT NULL COMMENT '资源路径',
  `resource_name` varchar(100) DEFAULT NULL COMMENT '资源名称',
  `roles` varchar(500) NOT NULL COMMENT '允许访问的角色，多个角色用逗号分隔',
  `description` varchar(500) DEFAULT NULL COMMENT '资源描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_path` (`resource_path`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源权限配置表';
```

## 🔧 配置说明

### 字段说明
- `resource_path`: 资源路径，如 `/im-business/api/friend/list`
- `resource_name`: 资源名称，便于理解的描述
- `roles`: 允许访问的角色，多个角色用逗号分隔，如 `ADMIN,USER`
- `description`: 资源描述信息
- `status`: 状态，1-启用，0-禁用

### 角色配置
- 支持多角色配置，用逗号分隔
- 常用角色：`ADMIN`、`USER`、`GUEST`
- 可以根据业务需要扩展新角色

## 🎯 核心特性

### 1. 动态加载
- 服务启动时从数据库加载配置
- 支持运行时配置刷新
- 无需重启服务即可生效

### 2. 高可用性
- 数据库异常时使用默认配置兜底
- 配置同时存储在内存和Redis中
- 支持配置的热更新

### 3. 完整的管理功能
- CRUD操作完整
- 支持批量操作
- 提供配置验证

### 4. 审计和监控
- 完整的操作日志
- 配置变更追踪
- 错误处理和恢复

## 📈 性能优化

### 1. 缓存机制
- 内存缓存：`Map<String, List<String>>`
- Redis缓存：持久化存储
- 启动时一次性加载，运行时高效访问

### 2. 批量操作
- 支持批量查询和更新
- 减少数据库访问次数
- 提高配置加载效率

## 🐛 故障排查

### 1. 配置加载失败
**现象**: 服务启动时报错或使用默认配置
**排查**:
```bash
# 检查数据库连接
# 查看启动日志
tail -f logs/im-auth.log | grep "ResourceServiceImpl"

# 检查数据库表是否存在
SHOW TABLES LIKE 'im_resource_role';

# 检查表数据
SELECT * FROM im_resource_role WHERE status = 1;
```

### 2. 配置不生效
**现象**: 修改配置后权限验证未生效
**解决**:
```bash
# 手动刷新配置
curl -X POST "http://localhost:8080/im-auth/api/resource/refresh"

# 检查Redis中的配置
redis-cli HGETALL RESOURCE_ROLES_MAP
```

### 3. 权限验证异常
**现象**: 接口访问被拒绝
**排查**:
```bash
# 查看当前配置
curl -X GET "http://localhost:8080/im-auth/api/resource/current"

# 检查具体资源路径配置
SELECT * FROM im_resource_role WHERE resource_path = '/your/api/path';
```

## 🔄 迁移步骤

如果你是从代码硬编码迁移到数据库：

1. **执行数据库脚本**
   ```bash
   mysql -u username -p database_name < script/sql/ddl/im_resource_role.sql
   ```

2. **验证数据导入**
   ```sql
   SELECT COUNT(*) FROM im_resource_role WHERE status = 1;
   ```

3. **重启服务**
   ```bash
   # 重启im-auth服务
   ```

4. **验证功能**
   ```bash
   # 检查配置加载
   curl -X GET "http://localhost:8080/im-auth/api/resource/current"
   ```

## 📝 最佳实践

1. **权限设计**
   - 遵循最小权限原则
   - 合理设计角色层次
   - 定期审查权限配置

2. **配置管理**
   - 重要配置变更要备份
   - 测试环境先验证
   - 生产环境谨慎操作

3. **监控告警**
   - 监控配置加载异常
   - 关注权限验证失败率
   - 定期检查配置完整性

---

## 🎉 总结

通过这次改造，资源权限配置实现了：
- ✅ **数据库化管理** - 告别硬编码
- ✅ **动态配置** - 无需重启即可生效  
- ✅ **完整的CRUD** - 便于运维管理
- ✅ **高可用性** - 异常情况下的兜底机制
- ✅ **性能优化** - 内存+Redis双重缓存

现在你可以通过数据库和API接口灵活管理所有资源权限配置了！🚀
