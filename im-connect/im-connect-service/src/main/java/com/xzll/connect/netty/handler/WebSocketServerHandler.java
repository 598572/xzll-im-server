package com.xzll.connect.netty.handler;


import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.config.ImMsgConfig;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.netty.heart.HeartBeatHandler;
import com.xzll.connect.service.UserStatusManagerService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.apache.skywalking.apm.toolkit.trace.Tracer;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * 自定义ChannelInboundHandler,处理消息请求
 * 优化内容：
 * 1. 修复内存泄漏问题
 * 2. 使用Spring依赖注入 (单例模式，@Sharable)
 * 3. 优化连接数统计
 * 4. 增强异步处理能力
 * 5. 增加消息长度限制
 * 6. 优化日志级别
 * 
 * 注意：此Handler使用@Sharable注解，所有连接共享同一个实例
 * 状态数据存储在LocalChannelManager中，确保线程安全
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    @Autowired
    private HeartBeatHandler heartBeatHandler;
    
    @Autowired
    private HandlerDispatcher handlerDispatcher;
    
    @Autowired
    private RedissonUtils redissonUtils;
    
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    
    @Autowired
    private ImMsgConfig imMsgConfig;
    
    @Autowired
    private UserStatusManagerService userStatusManagerService;

    // 使用LongAdder替代AtomicInteger，在高并发场景下性能更好
    private static final LongAdder connectionCount = new LongAdder();
    
    // 连接统计的时间戳，避免频繁输出
    private static final AtomicLong lastLogTime = new AtomicLong(System.currentTimeMillis());
    
    // 连接数统计阈值
    private static final long LOG_INTERVAL_MS = 10000; // 10秒输出一次
    
    // 消息长度限制
    private static final int MAX_MESSAGE_LENGTH = 10240; // 10KB
    
    // 线程池队列长度限制
    private static final int MAX_QUEUE_SIZE = 1000;

    private WebSocketServerHandshaker handShaker;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("当前 Trace ID: {}", TraceContext.traceId());
        super.channelActive(ctx);
        String channelId = ctx.channel().id().asLongText();
        
        // 连接数统计
        connectionCount.increment();
        
        // 减少日志频率，避免在高并发时产生过多日志
        long currentTime = System.currentTimeMillis();
        long lastLog = lastLogTime.get();
        if (currentTime - lastLog > LOG_INTERVAL_MS && lastLogTime.compareAndSet(lastLog, currentTime)) {
            log.info("当前连接数: {}, 新连接channelId: {}", connectionCount.sum(), channelId);
        } else {
            log.debug("客户端加入连接channelId: {}", channelId);
        }
        
        // 设置连接时间属性，用于连接时长统计
        NettyAttrUtil.setConnectTime(ctx.channel(), currentTime);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                log.debug("定时检测客户端端是否存活");
                heartBeatHandler.process(ctx);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            String channelId = ctx.channel().id().asLongText();
            String uid = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
            
            // 计算连接时长
            Long connectTime = NettyAttrUtil.getConnectTime(ctx.channel());
            if (connectTime != null) {
                long duration = System.currentTimeMillis() - connectTime;
                log.info("客户端断开连接：{}, 用户ID: {}, 连接时长: {}ms", channelId, uid, duration);
            } else {
                log.info("客户端断开连接：{}, 用户ID: {}", channelId, uid);
            }
            
            // 清理心跳失败计数，防止误杀重连用户
            if (heartBeatHandler instanceof com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl) {
                ((com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl) heartBeatHandler)
                    .cleanup(channelId);
                log.debug("已清理channelId={}的心跳数据", channelId);
            }
            
            // 【改为同步】清理用户状态
            // 原因：异步清理存在时序问题，可能误删用户重连后的新状态
            // Redis操作通常1-5ms，同步清理不会造成明显的性能问题
            if (uid != null) {
                try {
                    // 【重要】验证是否为当前用户的Channel，避免误删新连接
                    Channel currentChannel = LocalChannelManager.getChannelByUserId(uid);
                    if (currentChannel != null) {
                        String currentChannelId = currentChannel.id().asLongText();
                        if (!currentChannelId.equals(channelId)) {
                            // 用户已重连，旧连接清理时跳过
                            log.info("用户{}已重连，跳过旧连接的状态清理，旧channelId: {}, 新channelId: {}", 
                                uid, channelId, currentChannelId);
                            return; // 直接返回，不清理任何状态
                        }
                    }
                    
                    // 只有在以下情况才清理：
                    // 1. currentChannel == null（用户已离线）
                    // 2. currentChannelId == channelId（是当前连接）
                    
                    // 清理LocalChannelManager映射
                    LocalChannelManager.removeUserChannel(uid);
                    
                    // 清理Redis状态
                    userStatusManagerService.userDisconnectAfter(uid);
                    
                    log.info("用户{}状态清理完成，channelId: {}", uid, channelId);
                } catch (Exception e) {
                    log.error("用户断开连接后清理状态异常, uid: {}, channelId: {}", uid, channelId, e);
                }
            }
        } catch (Exception e) {
            log.error("处理连接断开异常", e);
        } finally {
            // 【重要】在finally中减少计数，确保旧连接断开时也会统计
            connectionCount.decrement();
            super.channelInactive(ctx);
        }
    }

    /**
     * 处理读
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 使用try-with-resources确保span正确关闭
        try {
            Tracer.createEntrySpan("Netty/ChannelRead", null);
            log.debug("当前 Trace ID: {}", TraceContext.traceId());
            
            // 传统的HTTP接入
            if (msg instanceof FullHttpRequest) {
                log.debug("=========处理Http请求接入=========");
                handleHttpRequest(ctx, ((FullHttpRequest) msg));
            }
            // WebSocket接入 PooledUnsafeDirectByteBuf
            else if (msg instanceof WebSocketFrame) {
                log.debug("=========处理websocket请求=========");
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            } else {
                log.debug("=========其他类型=========");
            }
        } finally {
            Tracer.stopSpan();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String uid = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
        log.error("[WebSocketServerHandler]_连接异常, uid: {}, channelId: {}", 
                  uid, ctx.channel().id().asLongText(), cause);
        
        // 确保连接被正确关闭
        if (ctx.channel().isActive()) {
            ctx.close();
        }
    }

    /**
     * 处理Http请求
     *
     * @param ctx
     * @param req
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        //Http解码失败，返回Http异常
        if (!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        Object uid = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
        if (null == uid) {
            //此连接上无authHandler设置的uid的话，说明没走authHandler 关闭连接！
            log.warn("没有获取到uid");
            ctx.channel().close();
            return;
        }
        String uidStr = String.valueOf(uid);

        //注意 此处 allowExtensions 要设置为true ，否则flutter客户端发送消息时 im-connect服务报错： io.netty.handler.codec.CorruptedFrameException: RSV != 0 and no extension negotiated, RSV:4
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true);
        handShaker = wsFactory.newHandshaker(req);
        if (handShaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            ChannelFuture handshake = handShaker.handshake(ctx.channel(), req);
            handshake.addListener(future -> {
                if (future.isSuccess()) {
                    log.info("WebSocket握手成功, uid: {}", uidStr);
                    
                    // 【重要】同步设置用户状态，确保后续心跳和消息处理时状态已就绪
                    // 顺序：先设置LocalChannelManager，再设置Redis状态
                    try {
                        // 1. 设置本地Channel映射
                        LocalChannelManager.addUserChannel(uidStr, ctx.channel());
                        log.debug("用户{}本地Channel映射设置完成", uidStr);
                        
                        // 2. 设置Redis在线状态和路由信息
                        userStatusManagerService.userConnectSuccessAfter(ImConstant.UserStatus.ON_LINE.getValue(), uidStr);
                        log.debug("用户{}Redis在线状态设置完成", uidStr);
                    } catch (Exception e) {
                        log.error("设置用户{}在线状态失败", uidStr, e);
                        // 状态设置失败，清理已设置的映射，关闭连接让用户重连
                        LocalChannelManager.removeUserChannel(uidStr);
                        ctx.close();
                        return;
                    }
                    
                    // 【异步】推送离线数据，避免阻塞握手流程
                    CompletableFuture.runAsync(() -> {
                        try {
                            // ❌ 【已移除】推送离线消息 - 客户端登录后会主动调用会话列表接口获取
                            // pushOfflineMessages(ctx, uid);
                            
                            // 推送离线好友请求
                            pushOfflineFriendRequests(ctx, uid);
                            
                            // 推送离线好友响应
                            pushOfflineFriendResponses(ctx, uid);
                        } catch (Exception e) {
                            log.error("推送离线数据异常, uid: {}", uidStr, e);
                        }
                    }, threadPoolTaskExecutor);
                } else {
                    log.warn("WebSocket握手失败, uid: {}, cause: {}", uidStr, future.cause().getMessage());
                    ctx.close();
                }
            });
        }
    }

    /**
     * 处理WebSocket消息
     *
     * @param ctx
     * @param frame
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        //判断是否是关闭连接的指令
        if (frame instanceof CloseWebSocketFrame) {
            log.info("[WebSocketServerHandler]_消息类型: close");
            try {
                handShaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            } catch (Exception e) {
                ReferenceCountUtil.release(frame);
                log.error("关闭WebSocket连接异常", e);
                ctx.close();
            }
            return;
        }
        
        //判断是否为ping消息
        if (frame instanceof PingWebSocketFrame) {
            long currentTime = System.currentTimeMillis();
            Long lastReadTime = NettyAttrUtil.getReaderTime(ctx.channel());
            long timeSinceLastRead = lastReadTime != null ? (currentTime - lastReadTime) : 0;
            
            log.debug("[WebSocketServerHandler]_消息类型: ping, 距离上次读取={}ms", timeSinceLastRead);
            
            NettyAttrUtil.updateReaderTime(ctx.channel(), currentTime);
            // 记录心跳响应（客户端主动发送ping）
            if (heartBeatHandler instanceof com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl) {
                ((com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl) heartBeatHandler)
                    .recordHeartbeatResponse(ctx, "ping");
            }
            // 修复内存泄漏：使用try-with-resources或者手动管理引用计数
            try {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            } catch (Exception e) {
                // 如果发送失败，需要释放retain的内容
                ReferenceCountUtil.release(frame.content());
                log.error("发送Pong消息失败", e);
            }
            return;
        }
        
        // 处理客户端回复的Pong消息（服务器发送Ping后，客户端回复Pong）
        if (frame instanceof PongWebSocketFrame) {
            log.debug("[WebSocketServerHandler]_消息类型: pong");
            NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
            // 记录心跳响应（客户端回复pong）
            if (heartBeatHandler instanceof com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl) {
                ((com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl) heartBeatHandler)
                    .recordHeartbeatResponse(ctx, "pong");
            }
            return;
        }
        
        // JSON 格式已废弃，仅支持 Protobuf 二进制消息
        if ((frame instanceof TextWebSocketFrame)) {
            log.warn("[WebSocketServerHandler]_收到文本消息，但系统已切换为仅支持 Protobuf 二进制格式，请升级客户端");
            ctx.close();
            return;
        }

        // Protobuf 二进制消息处理（唯一支持的格式）
        if (frame instanceof BinaryWebSocketFrame) {
            log.debug("[WebSocketServerHandler]_消息类型: protobuf 二进制");
            
            // 更新读取时间（任何消息都应该更新，包括业务消息）
            NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
            
            ByteBuf content = ((BinaryWebSocketFrame) frame).content();
            
            // 消息长度检查
            int readableBytes = content.readableBytes();
            if (readableBytes > MAX_MESSAGE_LENGTH) {
                log.warn("protobuf 消息长度超过限制: {} bytes", readableBytes);
                ctx.close();
                return;
            }
            
            try {
                // 解析 protobuf 消息
                byte[] bytes = new byte[readableBytes];
                content.getBytes(content.readerIndex(), bytes);
                com.xzll.grpc.ImProtoRequest protoRequest = com.xzll.grpc.ImProtoRequest.parseFrom(bytes);
                
                if (log.isDebugEnabled()) {
                    log.debug("[WebSocketServerHandler]_protobuf消息: type={}", protoRequest.getType());
                }
                
                // 检查线程池状态，避免任务堆积
                ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
                if (executor.getQueue().size() > MAX_QUEUE_SIZE) {
                    log.warn("线程池队列过长，拒绝处理protobuf消息: {}", executor.getQueue().size());
                    return;
                }
                
                // 分发&处理 protobuf 消息，业务和netty线程隔离
                CompletableFuture.runAsync(() -> {
                    try {
                        handlerDispatcher.dispatcher(ctx, protoRequest);
                    } catch (Exception e) {
                        log.error("[WebSocketServerHandler]_分发protobuf消息异常, type: {}", protoRequest.getType(), e);
                    }
                }, threadPoolTaskExecutor);
                
            } catch (Exception e) {
                log.error("[WebSocketServerHandler]_解析protobuf消息失败!", e);
            }
        }
    }

    /**
     * 【已废弃】异步推送离线消息（微信模式：每个会话只推送最新1条）
     * 
     * 废弃原因：
     * - 客户端登录后会主动调用会话列表接口获取所有会话信息
     * - 会话列表接口已包含每个会话的最新消息、时间、未读数
     * - 不再需要 WebSocket 主动推送离线消息
     * 
     * 推送策略：
     * 1. 每个会话只推送最新1条消息（作为会话列表的概要）
     * 2. 携带该会话的未读消息数量
     * 3. 用户点击会话后，调用HTTP接口拉取完整历史消息
     * 
     * 优点：
     * - 登录速度快（只推送少量消息）
     * - 用户体验好（类似微信、钉钉）
     * - 按需加载（进入会话才拉取完整历史）
     */
//    @Deprecated
//    private void pushOfflineMessages(ChannelHandlerContext ctx, Object uid) {
//        try {
//            // 1. 读取所有离线消息（最多1000条，避免内存溢出）
//            int maxFetchCount = Math.max(imMsgConfig.getC2cMsgConfig().getPushOfflineMsgCount(), 1000);
//            Collection<String> compressedMsgs = redissonUtils.getZSetRevRange(
//                ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + uid,
//                0,
//                maxFetchCount - 1
//            );
//
//            if (CollectionUtils.isEmpty(compressedMsgs)) {
//                log.debug("没有离线消息需要推送, uid: {}", uid);
//                return;
//            }
//
//            log.info("开始处理离线消息, uid: {}, 总数: {}", uid, compressedMsgs.size());
//
//            // 2. 按会话分组（chatId -> 消息列表）
//            Map<String, List<OfflineMessageWrapper>> chatMessagesMap = new LinkedHashMap<>();
//            int totalCount = 0;
//
//            for (String compressedMsg : compressedMsgs) {
//                try {
//                    // 解压消息
//                    String decompressedMsg = CompressionUtil.decompressFromBase64(compressedMsg);
//
//                    // 解析JSON获取chatId
//                    cn.hutool.json.JSONObject msgJson = JSONUtil.parseObj(decompressedMsg);
//                    String chatId = msgJson.getStr("chatId");
//
//                    if (chatId == null) {
//                        log.warn("离线消息缺少chatId，跳过, uid: {}", uid);
//                        continue;
//                    }
//
//                    // 按chatId分组
//                    chatMessagesMap.computeIfAbsent(chatId, k -> new ArrayList<>())
//                        .add(new OfflineMessageWrapper(decompressedMsg, msgJson));
//
//                    totalCount++;
//                } catch (Exception e) {
//                    log.error("解析离线消息失败, uid: {}", uid, e);
//                }
//            }
//
//            log.info("离线消息分组完成, uid: {}, 会话数: {}, 总消息数: {}",
//                uid, chatMessagesMap.size(), totalCount);
//
//            // 3. 每个会话只推送最新1条消息 + 未读数量
//            int successCount = 0;
//            int failCount = 0;
//
//            for (Map.Entry<String, List<OfflineMessageWrapper>> entry : chatMessagesMap.entrySet()) {
//                if (!ctx.channel().isActive()) {
//                    log.warn("连接已断开，停止推送离线消息, uid: {}, 已推送: {}/{}",
//                        uid, successCount, chatMessagesMap.size());
//                    break;
//                }
//
//                String chatId = entry.getKey();
//                List<OfflineMessageWrapper> messages = entry.getValue();
//                int unreadCount = messages.size();
//
//                // 取最新的1条消息（列表第一条就是最新的，因为ZSet是倒序）
//                OfflineMessageWrapper latestMsg = messages.get(0);
//
//                try {
//                    // 构建推送消息（添加未读数量）
//                    cn.hutool.json.JSONObject pushMsg = latestMsg.jsonObject;
//                    pushMsg.set("unreadCount", unreadCount); // 标记该会话的未读数量
//                    pushMsg.set("isLatestOnly", true);       // 标记这是会话概要，需要调用历史接口拉取完整消息
//
//                    String pushMsgStr = pushMsg.toString();
//
//                    // 推送消息
//                    ctx.channel().writeAndFlush(new TextWebSocketFrame(pushMsgStr))
//                        .addListener(future -> {
//                            if (!future.isSuccess()) {
//                                log.error("推送离线消息失败, uid: {}, chatId: {}", uid, chatId, future.cause());
//                            }
//                        });
//
//                    successCount++;
//
//                    log.debug("推送会话概要, uid: {}, chatId: {}, 未读数: {}", uid, chatId, unreadCount);
//
//                } catch (Exception e) {
//                    failCount++;
//                    log.error("推送离线消息失败, uid: {}, chatId: {}", uid, chatId, e);
//                }
//            }
//
//            log.info("离线消息推送完成（微信模式）, uid: {}, 会话数: {}, 成功: {}, 失败: {}, 总未读: {}",
//                uid, chatMessagesMap.size(), successCount, failCount, totalCount);
//
//        } catch (Exception e) {
//            log.error("推送离线消息异常, uid: {}", uid, e);
//        }
//    }
    
//    /**
//     * 离线消息包装类（内部使用）
//     */
//    private static class OfflineMessageWrapper {
//        cn.hutool.json.JSONObject jsonObject;
//
//        OfflineMessageWrapper(String json, cn.hutool.json.JSONObject jsonObject) {
//            this.jsonObject = jsonObject;
//        }
//    }
    
    /**
     * 推送离线好友请求
     */
    private void pushOfflineFriendRequests(ChannelHandlerContext ctx, Object uid) {
        try {
            String offlineKey = ImConstant.RedisKeyConstant.OFF_LINE_FRIEND_REQUEST_KEY + uid;
            Collection<String> offlineFriendRequests = redissonUtils.getZSetRevRange(offlineKey, 0, -1);
            
            if (!CollectionUtils.isEmpty(offlineFriendRequests)) {
                log.info("推送离线好友请求, uid: {}, count: {}", uid, offlineFriendRequests.size());
                
                int successCount = 0;
                for (String encodedMsg : offlineFriendRequests) {
                    if (ctx.channel().isActive()) {
                        try {
                            // Base64 解码
                            byte[] bytes = java.util.Base64.getDecoder().decode(encodedMsg);
                            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                            
                            ctx.channel().writeAndFlush(new BinaryWebSocketFrame(buf))
                                .addListener(future -> {
                                    if (future.isSuccess()) {
                                        log.debug("离线好友请求推送成功, uid: {}", uid);
                                    } else {
                                        log.error("发送离线好友请求失败, uid: {}", uid, future.cause());
                                    }
                                });
                            successCount++;
                        } catch (Exception e) {
                            log.error("解析离线好友请求失败, uid: {}", uid, e);
                        }
                    } else {
                        log.warn("连接已断开，停止推送离线好友请求, uid: {}", uid);
                        break;
                    }
                }
                
                // 推送完成后删除整个 ZSet
                if (successCount > 0) {
                    redissonUtils.delete(offlineKey);
                    log.info("离线好友请求推送完成，已清理Redis缓存, uid: {}, successCount: {}", uid, successCount);
                }
            }
        } catch (Exception e) {
            log.error("推送离线好友请求异常, uid: {}", uid, e);
        }
    }
    
    /**
     * 推送离线好友响应
     */
    private void pushOfflineFriendResponses(ChannelHandlerContext ctx, Object uid) {
        try {
            String offlineKey = ImConstant.RedisKeyConstant.OFF_LINE_FRIEND_RESPONSE_KEY + uid;
            Collection<String> offlineFriendResponses = redissonUtils.getZSetRevRange(offlineKey, 0, -1);
            
            if (!CollectionUtils.isEmpty(offlineFriendResponses)) {
                log.info("推送离线好友响应, uid: {}, count: {}", uid, offlineFriendResponses.size());
                
                int successCount = 0;
                for (String encodedMsg : offlineFriendResponses) {
                    if (ctx.channel().isActive()) {
                        try {
                            // Base64 解码
                            byte[] bytes = java.util.Base64.getDecoder().decode(encodedMsg);
                            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                            
                            ctx.channel().writeAndFlush(new BinaryWebSocketFrame(buf))
                                .addListener(future -> {
                                    if (future.isSuccess()) {
                                        log.debug("离线好友响应推送成功, uid: {}", uid);
                                    } else {
                                        log.error("发送离线好友响应失败, uid: {}", uid, future.cause());
                                    }
                                });
                            successCount++;
                        } catch (Exception e) {
                            log.error("解析离线好友响应失败, uid: {}", uid, e);
                        }
                    } else {
                        log.warn("连接已断开，停止推送离线好友响应, uid: {}", uid);
                        break;
                    }
                }
                
                // 推送完成后删除整个 ZSet
                if (successCount > 0) {
                    redissonUtils.delete(offlineKey);
                    log.info("离线好友响应推送完成，已清理Redis缓存, uid: {}, successCount: {}", uid, successCount);
                }
            }
        } catch (Exception e) {
            log.error("推送离线好友响应异常, uid: {}", uid, e);
        }
    }

    private String getWebSocketLocation(FullHttpRequest req) {
        String location = req.headers().get("Host") + ImConstant.WEBSOCKET_PATH;
        return "ws://" + location;
    }

    /**
     * 给客户端回复消息
     *
     * @param ctx
     * @param req
     * @param res
     */
    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        //返回应答给客户端
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        boolean keepAlive = "keep-alive".equalsIgnoreCase(req.headers().get("Connection"));
        if (!keepAlive || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    /**
     * 获取当前连接数
     */
    public static long getCurrentConnectionCount() {
        return connectionCount.sum();
    }
}