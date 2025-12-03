package com.xzll.stress

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import io.netty.buffer.Unpooled
import com.xzll.grpc._
import com.google.protobuf.ByteString
import com.xzll.common.util.ProtoConverterUtil
import com.xzll.common.constant.MsgFormatEnum
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * IM WebSocket 压力测试
 * 
 * 功能：
 * 1. 建立 WebSocket 连接
 * 2. 发送心跳（Ping/Pong）
 * 3. 发送单聊消息（C2C_SEND）
 * 4. 接收服务端推送（C2C_MSG_PUSH、ACK 等）
 * 
 * 使用方法：
 * mvn gatling:test
 * 
 * 自定义参数：
 * mvn gatling:test \
 *   -DTARGET_HOST=120.46.85.43 \
 *   -DTARGET_PORT=80 \
 *   -DMACHINE_ID=0 \
 *   -DUSERS_PER_MACHINE=10000 \
 *   -DRAMP_UP_TIME=120 \
 *   -DTEST_DURATION=600
 */
class ImWebSocketSimulation extends Simulation {

  // ==================== 压缩雪花算法参数（与服务端保持一致） ====================
  // 起始时间：2022-01-01 00:00:00 的秒时间戳
  val USER_ID_START_TIME = 1640995200L
  // 4位机器ID，最多16台机器
  val USER_MACHINE_ID_BITS = 4L
  // 6位序列号，每秒最多64个ID
  val USER_SEQUENCE_BITS = 6L
  // 位移计算
  val USER_MACHINE_SHIFT = USER_SEQUENCE_BITS                           // 6
  val USER_TIMESTAMP_SHIFT = USER_SEQUENCE_BITS + USER_MACHINE_ID_BITS  // 10
  val MAX_USER_SEQUENCE = (1L << USER_SEQUENCE_BITS) - 1                // 63
  val MAX_USER_MACHINE_ID = (1L << USER_MACHINE_ID_BITS) - 1            // 15
  
  // 用户ID生成的序列号计数器
  val userIdSequence = new AtomicLong(0L)
  // =========================================================================

  // ==================== 配置区 ====================
  // 优先读取 -D 系统属性，其次读取环境变量，最后使用默认值
  val targetHost = sys.props.getOrElse("TARGET_HOST", sys.env.getOrElse("TARGET_HOST", "120.46.85.43"))
  val targetPort = sys.props.getOrElse("TARGET_PORT", sys.env.getOrElse("TARGET_PORT", "80"))
  val wsUrl = s"ws://$targetHost:$targetPort/websocket"
  
  // 压测参数（可通过环境变量或 -D 参数覆盖）
  val machineId = sys.props.getOrElse("MACHINE_ID", sys.env.getOrElse("MACHINE_ID", "0")).toInt
  val usersPerMachine = sys.props.getOrElse("USERS_PER_MACHINE", sys.env.getOrElse("USERS_PER_MACHINE", "10000")).toInt
  val userIdOffset = machineId * usersPerMachine
  
  // 压测机器的用户机器ID（用于生成压缩雪花ID）
  val stressMachineId = (machineId % (MAX_USER_MACHINE_ID + 1)).toLong
  
  val rampUpTime = sys.props.getOrElse("RAMP_UP_TIME", sys.env.getOrElse("RAMP_UP_TIME", "120")).toInt.seconds
  val testDuration = sys.props.getOrElse("TEST_DURATION", sys.env.getOrElse("TEST_DURATION", "600")).toInt.seconds
  
  // 消息发送频率配置（可在启动时设置）
  val msgIntervalMs = sys.props.getOrElse("MSG_INTERVAL_MS", sys.env.getOrElse("MSG_INTERVAL_MS", "5000")).toInt  // 消息发送间隔（毫秒），默认 5 秒
  val heartbeatIntervalMs = sys.props.getOrElse("HEARTBEAT_INTERVAL_MS", sys.env.getOrElse("HEARTBEAT_INTERVAL_MS", "30000")).toInt  // 心跳间隔（毫秒），默认 30 秒
  val enableMsgSend = sys.props.getOrElse("ENABLE_MSG_SEND", sys.env.getOrElse("ENABLE_MSG_SEND", "true")).toBoolean  // 是否发送消息，默认开启
  
  // 计算预期 QPS
  val expectedQps = if (enableMsgSend && msgIntervalMs > 0) usersPerMachine * 1000 / msgIntervalMs else 0
  
  // Token 配置
  // 压测模式：使用 STRESS_TEST_BYPASS_TOKEN（需要服务端开启 im.netty.auth.stress-test-enabled=true）
  // 正常模式：使用真实 JWT Token
  val testToken = sys.props.getOrElse("TEST_TOKEN", sys.env.getOrElse("TEST_TOKEN", 
    "STRESS_TEST_BYPASS_TOKEN"))  // 默认使用压测专用Token
  // ===============================================

  println(s"╔════════════════════════════════════════════════════════════╗")
  println(s"║              Gatling IM 压力测试启动                      ║")
  println(s"╠════════════════════════════════════════════════════════════╣")
  println(s"║  目标服务器: $targetHost:$targetPort")
  println(s"║  机器ID: $machineId (压缩雪花机器ID: $stressMachineId)")
  println(s"╠════════════════════════════════════════════════════════════╣")
  println(s"║  用户数: $usersPerMachine")
  println(s"║  启动时间: $rampUpTime")
  println(s"║  测试时长: $testDuration")
  println(s"╠════════════════════════════════════════════════════════════╣")
  println(s"║  消息发送间隔: ${msgIntervalMs}ms (${msgIntervalMs/1000.0}秒)")
  println(s"║  心跳间隔: ${heartbeatIntervalMs}ms (${heartbeatIntervalMs/1000.0}秒)")
  println(s"║  是否发送消息: $enableMsgSend")
  println(s"║  预期消息QPS: $expectedQps (连接数/消息间隔)")
  println(s"╚════════════════════════════════════════════════════════════╝")

  // HTTP 协议配置
  val httpProtocol = http
    .baseUrl(s"http://$targetHost:$targetPort")
    .wsBaseUrl(wsUrl)
    .acceptHeader("*/*")
    .userAgentHeader("Gatling-IM-StressTest/1.0")

  /**
   * 生成压缩雪花算法用户ID（与服务端 generateCompactUserId 保持一致）
   * 
   * 算法：[32位时间戳][4位机器ID][6位序列号] = 42位 (10-12位数字)
   * 
   * @param virtualUserId 虚拟用户编号（用于确保唯一性）
   * @return 压缩格式的用户ID字符串
   */
  def generateCompactUserId(virtualUserId: Long): String = {
    // 当前时间戳（秒级）减去起始时间
    val timestamp = System.currentTimeMillis() / 1000 - USER_ID_START_TIME
    
    // 使用虚拟用户ID作为序列号的一部分，确保唯一性
    // 每个虚拟用户有唯一的 session.userId，范围是 0 ~ usersPerMachine
    val sequence = (virtualUserId + userIdOffset) & MAX_USER_SEQUENCE
    
    // 组装压缩ID：[32位时间戳][4位机器ID][6位序列号]
    val compactId = (timestamp << USER_TIMESTAMP_SHIFT) | 
                    (stressMachineId << USER_MACHINE_SHIFT) | 
                    sequence
    
    compactId.toString
  }

  // WebSocket 场景定义
  val scn = scenario(s"IM Stress Test - Machine $machineId")
    .exec { session =>
      // 使用压缩雪花算法生成用户ID（10-12位数字）
      val userId = generateCompactUserId(session.userId)
      session.set("userId", userId)
    }
    
    // 1. 建立 WebSocket 连接
    .exec(
      ws("Connect WebSocket")
        .connect(s"$wsUrl?userId=$${userId}")
        .header("token", testToken)
        .header("uid", "${userId}")
        .await(10.seconds)(
          ws.checkBinaryMessage("Handshake Check")
            .silent  // 不记录到报告，减少干扰
        )
    )
    
    // 2. 循环发送心跳和消息（持续测试时长）
    .during(testDuration) {
      // 按配置的心跳间隔发送 Ping 心跳
      pace(heartbeatIntervalMs.milliseconds)
        .exec(
          ws("Send Ping Heartbeat")
            .sendBytes(Array[Byte]())  // Ping 帧由 Netty 自动处理
        )
        
        // 按配置的消息间隔发送单聊消息（可通过 ENABLE_MSG_SEND 开关控制）
        .doIf(_ => enableMsgSend) {
          pause(msgIntervalMs.milliseconds)
            .exec { session =>
              val fromUser = session("userId").as[String]
              // 随机选择一个接收方（使用压缩雪花算法生成）
              val randomVirtualUserId = scala.util.Random.nextInt(usersPerMachine * 10).toLong
              val toUser = generateCompactUserId(randomVirtualUserId)
              
              val message = buildC2CMessage(fromUser, toUser, s"Stress test from $fromUser")
              session.set("message", message)
            }
            .exec(
              ws("Send C2C Message")
                .sendBytes("${message}")
            )
        }
    }
    
    // 3. 关闭连接
    .exec(
      ws("Close WebSocket").close
    )

  // 负载模型：逐步增加用户
  setUp(
    scn.inject(
      rampUsers(usersPerMachine).during(rampUpTime)
    ).protocols(httpProtocol)
  ).assertions(
    global.successfulRequests.percent.gt(95),  // 成功率 > 95%
    global.responseTime.max.lt(10000)          // 最大响应时间 < 10s
  )

  // ==================== Protobuf 构建方法 ====================
  
  /**
   * 构建单聊消息（C2C_SEND）
   * 
   * 消息结构：
   * ImProtoRequest {
   *   type = C2C_SEND
   *   payload = C2CSendReq.toByteArray()
   * }
   */
  def buildC2CMessage(from: String, to: String, content: String): Array[Byte] = {
    // 生成客户端消息ID（UUID）
    val clientMsgId = UUID.randomUUID().toString
    val sendTime = System.currentTimeMillis()
    
    // 构建 C2CSendReq（优化版：clientMsgId=bytes, from/to=fixed64）
    val sendReq = C2CSendReq.newBuilder()
      .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(clientMsgId))  // UUID String -> bytes
      .setMsgId(0L)  // 留空，服务端会自动生成
      .setFrom(ProtoConverterUtil.snowflakeStringToLong(from))  // String -> fixed64
      .setTo(ProtoConverterUtil.snowflakeStringToLong(to))  // String -> fixed64
      .setFormat(MsgFormatEnum.TEXT_MSG.getCode)  // 文本消息
      .setContent(content)
      .setTime(sendTime)  // fixed64
      .build()
    
    // 包装为 ImProtoRequest
    val protoRequest = ImProtoRequest.newBuilder()
      .setType(MsgType.C2C_SEND)
      .setPayload(ByteString.copyFrom(sendReq.toByteArray))
      .build()
    
    protoRequest.toByteArray
  }
  
  /**
   * 构建心跳消息（可选，如果需要发送 Protobuf 心跳）
   * 
   * 注意：WebSocket 的 Ping/Pong 帧由 Netty 自动处理，
   * 通常不需要发送 Protobuf 心跳消息
   */
  def buildHeartbeat(): Array[Byte] = {
    // 如果你的服务端需要特定的心跳消息，可以在这里构建
    // 例如：
    // val heartbeat = HeartbeatReq.newBuilder().setTimestamp(System.currentTimeMillis()).build()
    // val protoRequest = ImProtoRequest.newBuilder().setType(MsgType.HEARTBEAT).setPayload(...).build()
    // protoRequest.toByteArray
    
    // 默认返回空数组（使用 WebSocket Ping 帧）
    Array[Byte]()
  }
}

