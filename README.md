
# 前言
## 蝎子莱莱爱打怪 的 IM开源项目 (服务端)

[![GitHub Stars](https://img.shields.io/github/stars/598572/xzll-im-server?style=flat-square&logo=github)](https://github.com/598572/xzll-im-server)
[![GitHub Forks](https://img.shields.io/github/forks/598572/xzll-im-server?style=flat-square&logo=github)](https://github.com/598572/xzll-im-server)
[![License](https://img.shields.io/github/license/598572/xzll-im-server?style=flat-square)](https://github.com/598572/xzll-im-server/blob/main/LICENSE)
[![GitHub Issues](https://img.shields.io/github/issues/598572/xzll-im-server?style=flat-square)](https://github.com/598572/xzll-im-server/issues)

### 📱 项目仓库
| 端侧 | 技术栈 | GitHub地址 | 描述 |
|------|--------|------------|------|
| **🖥️ 服务端** | Java + Spring Cloud + Dubbo + Netty | [xzll-im-server](https://github.com/598572/xzll-im-server) | 分布式IM后端服务 |
| **📱 客户端** | Flutter + Dart | [xzll-im-flutter-client](https://github.com/598572/xzll-im-flutter-client) | 跨平台移动客户端 |


与im结缘是在2022年，因为此类系统有足够大的挑战性，所以我对此如痴如醉，之前做过架构以及细节方面的设计，但是一直没有落地。不落地的设计不是好设计。所以有了这个项目。
目前项目处于前期阶段，后期一点点完善并将补上架构图和我能想到的所有设计细节！

想要设计一个好的im系统，是很有难度的，本项目将尽可能达到以下几点：
- 高并发
- 高可用
- 高性能
- 稳定可靠
- 灵活好扩展
- 可观测



下边我们了解下总体设计和详细情况。以便有兴趣的人员学习/参与进来，相关群和我个人微信请到此页面最底部。

# 1、总体设计

## 1.1、架构设计

### 🏗️ 系统架构总览

![img.png](doc/images/img_2.png)


---

## 🌐 1.2、物理部署拓扑图

### 🏗️ 集群架构部署
![img.png](doc/images/img_3.png)

### 📋 节点配置表

| 节点IP | 节点角色 | 部署组件 | 端口 | 用途 |
|---------|---------|----------|------|------|
| **192.168.1.101** | 负载均衡+代理 | Nginx, FRP Client, IM服务(规划) | 80/443 | 流量入口、内网穿透 |
| **192.168.1.102** | 监控节点 | Prometheus, Grafana, Skywalking, IM服务(规划) | 9090/3000/8080 | 系统监控、链路追踪 |
| **192.168.1.150** | 应用服务 | IM微服务群、Nacos、Jenkins、Docker | 8081-8085/10001/8848 | 核心业务逻辑 |
| **192.168.1.130** | 集群主节点 | ZK Master, RMQ Master, HBase Master, HDFS NameNode, MySQL Master | 2181/9876/3306 | 集群协调、数据管理、主数据库 |
| **192.168.1.131** | 集群从节点1 | ZK Follower, RMQ Follower, HBase RegionServer, Redis, MySQL Slave | 2181/9876/6379/3306 | 数据存储、缓存、从数据库 |
| **192.168.1.132** | 集群从节点2 | ZK Follower, RMQ Follower, HBase RegionServer, ES | 2181/9876/9200 | 数据存储、搜索 |

---

## 🔄 1.3、核心业务流程

### 📱 用户登录流程
```mermaid
sequenceDiagram
    participant C as 客户端
    participant G as Gateway
    participant A as Auth服务
    participant M as MySQL
    participant R as Redis
    participant CON as Connect服务

    C->>G: 登录请求(username/password/device_type)
    G->>A: 转发认证请求
    A->>M: 查询用户信息验证凭据
    M->>A: 返回用户数据
    A->>A: 生成JWT Token
    A->>R: 存储Token和用户ID映射
    A->>G: 返回JWT Token
    G->>C: 登录成功响应
    Note over C: 客户端使用Token建立WebSocket连接
    C->>CON: WebSocket连接(携带Token)
    CON->>R: 验证Token有效性
    R->>CON: Token验证通过
    CON->>C: WebSocket连接建立成功
```

### 💬 单聊消息发送流程

#### 📊 流程图版本（逻辑流向）
```mermaid
flowchart LR
    A[客户端发送消息] --> B[Connect服务接收]
    B --> C[策略分发C2CMsgSendStrategyImpl]
    
    %% 并行处理分支
    C --> D{并行处理}
    D --> E[发送到RocketMQ<br/>异步业务处理]
    D --> F[查询接收方在线状态<br/>实时消息路由]
    
    %% 异步业务处理分支
    E --> G[Business服务消费MQ]
    G --> H[C2CSendMsgHandler处理]
    H --> I{并行数据存储}
    I --> J[MySQL存储会话信息]
    I --> K[HBase存储消息记录]
    K --> L[触发ES数据同步]
    L --> M[DataSync批量写入ES]
    J --> N[增加未读计数Redis]
    K --> N
    N --> O[Dubbo RPC发送Server ACK]
    O --> P[推送ACK给发送方]
    
    %% 实时消息路由分支
    F --> Q{接收方状态判断}
    Q -->|在线且在本机| R[直接推送WebSocket消息]
    Q -->|在线但在其他机器| S[Dubbo RPC转发到目标机器]
    Q -->|离线| T[发送离线消息事件]
    
    S --> U[目标机器推送消息]
    T --> V[Business处理离线消息]
    V --> W[更新HBase状态为离线]
    W --> X[存储离线消息到Redis]
    X --> Y[伪造未读ACK给发送方]
    
    %% 消息确认流程
    R --> Z[接收方发送ACK]
    U --> Z
    Z --> AA[Connect转发ACK到MQ]
    AA --> BB[Business处理ACK]
    BB --> CC[更新HBase消息状态]
    CC --> DD[触发状态更新ES同步]
    CC --> EE[清零未读计数if已读]
    EE --> FF[Dubbo发送ACK给发送方]
    FF --> GG[推送ACK确认]
    
    %% 样式定义
    classDef connectService fill:#e1f5fe
    classDef businessService fill:#f3e5f5
    classDef mq fill:#fff3e0
    classDef storage fill:#e8f5e8
    classDef client fill:#ffebee
    
    class B,C,F,Q,Z,AA connectService
    class G,H,V,BB businessService
    class E,L,T,AA mq
    class J,K,M,W,CC storage
    class A,R,U,Y,GG client
```

#### ⏰ 时序图版本（详细交互）
```mermaid
sequenceDiagram
    participant C1 as 发送方客户端
    participant CON as Connect服务
    participant MQ as RocketMQ
    participant BIZ as Business服务
    participant M as MySQL
    participant H as HBase
    participant R as Redis
    participant DS as DataSync服务
    participant ES as Elasticsearch
    participant C2 as 接收方客户端

    Note over C1,C2: 1. 消息接收与策略分发
    C1->>CON: 发送消息(WebSocket)
    CON->>CON: 策略分发(C2CMsgSendStrategyImpl)
    
    Note over CON,C2: 2. 核心并行处理：异步数据持久化 & 实时消息推送
    par 异步数据持久化流程
        CON->>MQ: 发送到RocketMQ(异步削峰)
        MQ->>BIZ: 消费消息(C2CMsgEventConsumer)
        BIZ->>BIZ: C2CSendMsgHandler处理
        par 并行数据存储
            BIZ->>M: 更新/创建会话信息
        and 
            BIZ->>H: 存储消息记录
            H->>BIZ: HBase存储成功
            BIZ->>MQ: 发送ES同步消息
            MQ->>DS: im-data-sync消费消息
            DS->>ES: 批量写入搜索引擎
        end
        BIZ->>R: 增加接收方未读计数
        BIZ->>CON: Dubbo RPC发送Server ACK
        CON->>C1: 推送Server ACK确认
    and 实时消息推送流程
        CON->>R: 查询接收方在线状态
        alt 接收方在线且在本机
            CON->>C2: 直接推送消息(WebSocket)
        else 接收方在线但在其他机器
            CON->>CON: Dubbo RPC转发到目标机器
            CON->>C2: 推送消息(WebSocket)
        else 接收方离线
            CON->>MQ: 发送离线消息事件
            MQ->>BIZ: 处理离线消息(C2COffLineMsgHandler)
            BIZ->>H: 更新消息状态为离线
            H->>BIZ: 更新成功确认
            BIZ->>MQ: 发送状态更新同步消息
            BIZ->>R: 存储离线消息缓存
            BIZ->>CON: 伪造未读ACK给发送方
            CON->>C1: 推送未读ACK
        end
    end
    
    Note over C2,C1: 3. 消息确认阶段(异步处理)
    C2->>CON: 发送已读/未读ACK
    CON->>MQ: 转发ACK到RocketMQ
    MQ->>BIZ: 处理ACK(C2CClientReceivedAckMsgHandler)
    BIZ->>H: 更新消息状态
    H->>BIZ: 更新成功确认
    BIZ->>MQ: 发送状态更新同步消息
    BIZ->>R: 清零未读计数(如果已读)
    BIZ->>CON: Dubbo RPC发送ACK给发送方
    CON->>C1: 推送ACK确认
```

---

## 1.4、表设计

目前表结构详见：[表结构](script/sql/ddl/xzll_im_ddl.sql)


# 2、技术栈与功能总结


## 2.1、技术栈总览

### 📊 技术架构图谱

| 技术层次 | 技术选型 | 状态 | 用途说明 |
|----------|----------|------|----------|
| **📱 客户端层** | Flutter + Dart | ✅ | 跨平台移动客户端开发 |
| **🌐 接入层** | Nginx | ✅ | 负载均衡、反向代理、HTTPS终结 |
| **🚪 网关层** | Spring Cloud Gateway | ✅ | 统一API网关、路由分发、限流熔断 |
| **🔧 业务层** | Spring Boot + Spring Cloud | ✅ | 微服务应用框架、服务治理 |
| **🔗 通信层** | Netty + WebSocket + Dubbo | ✅ | 长连接通信、RPC服务调用 |
| **🔐 安全层** | OAuth2 + Spring Security + JWT | ✅ | 身份认证、权限控制、令牌管理 |
| **⚙️ 中间件层** | Nacos + ZooKeeper + RocketMQ | ✅ | 服务注册发现、消息队列、配置管理 |
| **💾 存储层** | MySQL + HBase + Redis + ES | ✅ | 关系数据、大数据、缓存、搜索 |
| **📊 监控层** | Prometheus + Grafana + Skywalking | ✅/⏳ | 性能监控、链路追踪、可视化 |
| **🚀 部署层** | Jenkins + Docker Compose | ✅ | CI/CD流水线、容器编排部署 |

---

### 🔧 详细技术栈

| 分类 | 技术 | 版本         | 状态 | 说明 |
|------|------|------------|------|------|
| **📱 前端** | **Flutter** | 3.24+      | ✅ | 跨平台UI框架，支持Android/iOS |
| **📱 前端** | **Dart** | 3.4.4+     | ✅ | 现代化编程语言，强类型安全 |
| **📱 前端** | **WebSocket Channel** | 2.1.0      | ✅ | WebSocket连接管理 |
| **📱 前端** | **HTTP** | 0.13.3     | ✅ | RESTful API调用 |
| **📱 前端** | **SharedPreferences** | 2.0.15     | ✅ | 本地数据存储 |
| **📱 前端** | **Image Picker** | 0.8.6      | ✅ | 图片选择器 |
| **📱 前端** | **Flutter Sound** | 9.2.13     | ✅ | 音频录制播放 |
| **📱 前端** | **Permission Handler** | 10.2.0     | ✅ | 权限管理 |
| **🖥️ 后端** | **Java** | 11         | ✅ | 核心编程语言 |
| **🖥️ 后端** | **Spring Boot** | 2.7.0      | ✅ | 应用开发框架 |
| **🖥️ 后端** | **Spring Cloud** | 2021.0.3   | ✅ | 微服务治理框架 |
| **🖥️ 后端** | **Spring Cloud Alibaba** | 2021.0.1.0 | ✅ | 阿里云微服务套件 |
| **🖥️ 后端** | **Spring Security** | 5.7.x      | ✅ | 安全认证框架 |
| **🖥️ 后端** | **OAuth2** | 2.2.5      | ✅ | 认证授权协议 |
| **🖥️ 后端** | **Netty** | 4.1.75     | ✅ | 高性能网络通信框架 |
| **🖥️ 后端** | **Dubbo** | 3.0.7      | ✅ | 高性能RPC框架 |
| **🖥️ 后端** | **MyBatis Plus** | 3.5.0      | ✅ | 持久层ORM框架 |
| **🖥️ 后端** | **ShardingSphere** | 5.2.1      | ✅ | 分库分表中间件 |
| **🖥️ 后端** | **Druid** | 1.2.8      | ✅ | 数据库连接池 |
| **🖥️ 后端** | **Hutool** | 5.6.6      | ✅ | Java工具类库 |
| **🖥️ 后端** | **Lombok** | 1.18.20    | ✅ | 代码生成工具 |
| **🖥️ 后端** | **FastJSON** | 1.2.46     | ✅ | JSON解析库 |
| **⚙️ 中间件** | **Nacos** | 2.0.3      | ✅ | 微服务注册中心、配置中心、服务发现 |
| **⚙️ 中间件** | **ZooKeeper** | 3.5.1      | ✅ | 分布式协调服务、Dubbo专用注册中心 |
| **⚙️ 中间件** | **RocketMQ** | 5.3.0      | ✅ | 分布式消息队列、削峰填谷 |
| **⚙️ 中间件** | **Nginx** | 1.24.0     | ✅ | 负载均衡、反向代理 |
| **💾 存储** | **MySQL** | 8.0.23     | ✅ | 关系型数据库、主从复制 |
| **💾 存储** | **HBase** | 2.6.1      | ✅ | 分布式NoSQL、海量消息存储 |
| **💾 存储** | **Redis** | 6.2.6      | ✅ | 内存数据库、缓存、分布式锁 |
| **💾 存储** | **Redisson** | 3.14.0     | ✅ | Redis Java客户端、分布式锁实现 |
| **💾 存储** | **Elasticsearch** | 7.17.5     | ✅ | 搜索引擎、消息全文检索 |
| **💾 存储** | **HDFS** | 3.3.5      | ✅ | 分布式文件系统、HBase底层存储 |
| **💾 存储** | **Hadoop** | 3.3.5      | ✅ | 分布式计算存储框架 |
| **📊 运维** | **Prometheus** | Latest     | ✅ | 系统监控、指标采集 |
| **📊 运维** | **Grafana** | Latest     | ✅ | 监控数据可视化 |
| **📊 运维** | **Skywalking** | 8.x        | ⏳ | APM性能监控、链路追踪 |
| **📊 运维** | **Jenkins** | 2.452      | ✅ | CI/CD持续集成部署、Pipeline脚本 |
| **📊 运维** | **Docker** | 26.1.4     | ✅ | 应用容器化 |
| **📊 运维** | **Docker Compose** | 3.9        | ✅ | 多容器应用编排、一键部署 |
| **🔧 工具** | **Git** | 2.15.0     | ✅ | 版本控制 |
| **🔧 工具** | **Maven** | 3.9.9      | ✅ | 项目构建管理 |
| **🔧 工具** | **IntelliJ IDEA** | 2024.3.3   | ✅ | Java开发IDE |
| **🔧 工具** | **Android Studio** | 2024.1.1   | ✅ | Flutter开发IDE |
| **🔧 工具** | **Postman** | Latest     | ✅ | API接口测试 |



---

### ⏳ **待集成技术**

| 技术 | 优先级 | 说明                |
|------|--------|-------------------|
| **Protocol Buffers** | 高 | 高效序列化协议，替换JSON    |
| **Sentinel** | 高 | 流量控制、熔断降级         |
| **Skywalking** | 中 | 完善APM链路追踪         |
| **JMeter** | 中 | 压力测试工具            |
| **Redis Sentinel** | 中 | Redis哨兵高可用部署     |
| **ES Cluster** | 低 | Elasticsearch集群部署 |
| **K8s** | 低 | 未来大规模容器集群管理       |



## 2.2、核心功能概览

### ✅ **已实现功能**
- 🔐 **用户认证**: 注册、登录（OAuth2 + JWT）
- 💬 **单聊消息**: 文字消息发送、撤回、ACK确认  
- 📊 **消息存储**: HBase分布式存储 + Elasticsearch搜索
- 🔄 **离线消息**: Push推送机制（Pull拉取开发中）
- ⚡ **实时通信**: WebSocket长连接 + Netty高性能
- 🆔 **消息ID**: 分布式唯一ID生成算法
- 💓 **心跳检测**: 服务端自动剔除超时连接

### 🚧 **开发中功能**  
- 📱 **Flutter客户端**: 聊天界面和交互逻辑
- 📋 **会话管理**: 最近会话列表

### 📋 **规划功能**
- 🗨️ **群聊系统**: 群组消息、群管理（写扩散模型）
- 👥 **好友系统**: 好友关系管理  
- 📁 **多媒体消息**: 图片、语音、视频消息
- 🔍 **消息搜索**: 基于Elasticsearch的全文检索
- 📱 **客户端增强**: 断线重连、消息排序、防重处理
- 🎯 **会话功能**: 置顶、删除、免打扰
- 📹 **音视频通话**: WebRTC实时通信
- 📺 **直播功能**: 实时直播推流

### 🤝 **参与开发**

> 💡 **想了解详细开发进度？** 查看 [📋 功能开发进度表](CONTRIBUTING.md#%E5%8A%9F%E8%83%BD%E5%BC%80%E5%8F%91%E8%BF%9B%E5%BA%A6%E8%A1%A8)
> 
> 🚀 **想参与贡献？** 阅读 [🤝 参与贡献指南](CONTRIBUTING.md) 了解如何加入我们


# 3、如何参与开发本项目？

详见文档：[CONTRIBUTING](CONTRIBUTING.md)

# 4、如何启动并运行此项目？

## 4.1、🖥️ 服务端启动

### IDEA中运行
直接下载此项目main分支，一键启动即可（因为相关依赖的中间件都已经部署在服务器上了，公网可流畅连接）：
![img.png](doc/images/img.png)

> 💡 **开发者详细指南**: 查看 [详细启动指南](CONTRIBUTING.md#%E8%AF%A6%E7%BB%86%E5%90%AF%E5%8A%A8%E6%8C%87%E5%8D%97) 了解完整的开发环境搭建、微服务启动顺序、常见问题解决等

## 4.2、📱 客户端启动

### Flutter客户端
1. **前往客户端仓库**: [xzll-im-flutter-client](https://github.com/598572/xzll-im-flutter-client)
2. **快速启动**:
   ```bash
   git clone https://github.com/598572/xzll-im-flutter-client.git
   cd xzll-im-flutter-client
   flutter pub get
   flutter run
   ```

> 💡 **Flutter开发详细指南**: 查看 [详细启动指南](CONTRIBUTING.md#%E8%AF%A6%E7%BB%86%E5%90%AF%E5%8A%A8%E6%8C%87%E5%8D%97) 了解Flutter环境配置、调试技巧、常见问题解决等

## 4.3、📺 演示效果

编写中.....

> 💡 **提示**: 客户端需要与服务端配合使用，请确保服务端已正常启动后再运行客户端。

# 为了方便协作，可加我微信，然后我给拉进此项目相关群聊。

个人微信 ：

![img_1.png](doc/images/img_1.png)

想进群沟通的话，请加我微信。

邮箱 ： h163361631@163.com

本人稀土掘金博客：：https://juejin.cn/user/1239904847403927  
一些中间件部署，各类技术知识点和IM开发过程中遇到的问题，甚至硬件环境搭建都会记录到 **此博客**





