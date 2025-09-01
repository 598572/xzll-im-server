# gRPC迁移指南

## 概述
本文档描述了如何将项目中的Dubbo RPC调用替换为gRPC，以提升性能和稳定性。

## 已完成的工作

### 1. 依赖配置
- 在根pom.xml中添加了gRPC版本变量
- 在im-connect-service和im-business-service中添加了gRPC依赖
- 创建了gRPC配置文件

### 2. 服务定义
- 创建了`message_service.proto`文件，定义了消息转发服务的gRPC接口
- 创建了`MessageServiceGrpcImpl`类，实现了gRPC服务
- 创建了`GrpcClientManager`类，管理gRPC客户端连接
- 创建了`GrpcServerConfig`类，配置gRPC服务器

### 3. 代码修改
- 修改了`C2CMsgSendStrategyImpl`类，添加了gRPC客户端管理器
- 暂时保留了原有的Dubbo调用逻辑，等待gRPC服务完全配置完成

## 待完成的工作

### 1. 编译Proto文件
需要添加protobuf-maven-plugin插件来编译proto文件生成Java类：

```xml
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocExecutable>protoc</protocExecutable>
        <protoSourceRoot>${project.basedir}/src/main/proto</protoSourceRoot>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
                <goal>compile-custom</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 2. 替换所有Dubbo调用
需要修改以下文件中的Dubbo调用：

#### im-business-service中的消息处理器：
- `C2CSendMsgHandler.java`
- `C2COffLineMsgHandler.java`
- `C2CClientWithdrawMsgHandler.java`
- `C2CClientReceivedAckMsgHandler.java`

#### 替换模式：
```java
// 原来的Dubbo调用
@DubboReference
private RpcSendMsg2ClientApi rpcSendMsg2ClientApi;

// 替换为gRPC调用
@Resource
private GrpcClientManager grpcClientManager;

// 调用方式
MessageServiceGrpc.MessageServiceBlockingStub stub = 
    grpcClientManager.getBlockingStub(targetIp, targetPort);
WebBaseResponse response = stub.responseServerAck2Client(grpcRequest);
```

### 3. 移除Dubbo依赖
完成迁移后，可以逐步移除：
- `@DubboReference`注解
- `@DubboService`注解
- Dubbo相关的依赖
- `DubboNetworkInitializer`类

### 4. 配置gRPC服务发现
如果需要服务发现功能，可以集成：
- Nacos作为gRPC服务注册中心
- 或者使用gRPC的原生服务发现机制

## 迁移步骤

### 第一阶段：准备
1. ✅ 添加gRPC依赖
2. ✅ 创建proto文件
3. ✅ 创建基础类
4. ⏳ 编译proto文件生成Java类

### 第二阶段：逐步替换
1. ⏳ 替换`C2CMsgSendStrategyImpl`中的跨服务器调用
2. ⏳ 替换业务服务中的消息处理器
3. ⏳ 测试gRPC调用功能

### 第三阶段：清理
1. ⏳ 移除Dubbo注解和依赖
2. ⏳ 清理Dubbo配置
3. ⏳ 性能测试和优化

## 优势

### gRPC vs Dubbo
- **性能**: gRPC基于HTTP/2，性能更好
- **跨语言**: 支持多种编程语言
- **标准化**: 基于Protocol Buffers，更标准化
- **云原生**: 更适合微服务和云原生架构
- **流式处理**: 支持流式RPC调用

## 注意事项

1. **端口配置**: 确保gRPC端口(9090)不被其他服务占用
2. **TLS配置**: 生产环境建议启用TLS加密
3. **负载均衡**: 考虑使用gRPC的负载均衡机制
4. **监控**: 添加gRPC调用的监控和日志
5. **降级策略**: 保留原有的Dubbo调用作为降级方案

## 测试建议

1. 单元测试gRPC服务实现
2. 集成测试gRPC客户端和服务端
3. 性能测试对比Dubbo和gRPC
4. 压力测试验证gRPC的稳定性

## 总结

gRPC迁移是一个渐进式的过程，建议分阶段进行，确保每个阶段都经过充分测试。gRPC相比Dubbo具有更好的性能和标准化程度，是现代化微服务架构的更好选择。 