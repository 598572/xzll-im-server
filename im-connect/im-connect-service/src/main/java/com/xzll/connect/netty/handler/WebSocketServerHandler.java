package com.xzll.connect.netty.handler;


import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.config.ImMsgConfig;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.netty.heart.HeartBeatHandler;
import com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl;
import com.xzll.connect.service.UserStatusManagerService;
import com.xzll.connect.service.impl.UserStatusManagerServiceImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.SpanRef;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.apache.skywalking.apm.toolkit.trace.Tracer;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * 自定义ChannelInboundHandler,处理消息请求
 */
@Slf4j
@ChannelHandler.Sharable
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {


    private static final HeartBeatHandler heartBeatHandler = SpringUtil.getBean(NettyServerHeartBeatHandlerImpl.class);
    private static final HandlerDispatcher handlerDispatcher = SpringUtil.getBean(HandlerDispatcher.class);
    private static final RedissonUtils redissonUtils = SpringUtil.getBean(RedissonUtils.class);
    private static final ThreadPoolTaskExecutor threadPoolTaskExecutor = SpringUtil.getBean(ThreadPoolTaskExecutor.class);
    private static final ImMsgConfig imMsgConfig = SpringUtil.getBean(ImMsgConfig.class);
    private static final UserStatusManagerService userStatusManagerService = SpringUtil.getBean(UserStatusManagerServiceImpl.class);

    //这里用来对连接数进行记数,每两秒输出到控制台
    private static final AtomicInteger nConnection = new AtomicInteger();

    static {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            System.out.println("当前连接数: " + nConnection.get());
        }, 0, 10, TimeUnit.SECONDS);
    }

    private WebSocketServerHandshaker handShaker;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("当前 Trace ID: {}", TraceContext.traceId());
        super.channelActive(ctx);
        String channelId = ctx.channel().id().asLongText();
        //添加连接
        log.info("客户端加入连接channelId: {}", channelId);
        //不能在此处设置用户信息 因为在这个阶段无法拿到uid新增 attr设置的只能是本地 不能垮进程
        nConnection.incrementAndGet();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                log.info("定时检测客户端端是否存活");
                heartBeatHandler.process(ctx);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asLongText();
        //断开连接
        log.info("客户端断开连接：{}", channelId);
        String uid = ctx.channel().attr(ImConstant.USER_ID_KEY).get();
        //清除用户登录的服务器信息
        LocalChannelManager.removeUserChannel(uid);
        userStatusManagerService.userDisconnectAfter(uid);
        nConnection.decrementAndGet();
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

        SpanRef span = Tracer.createEntrySpan("Netty/ChannelRead", null);
        try {
            log.info("当前 Trace ID: {}", TraceContext.traceId());
            // 传统的HTTP接入
            if (msg instanceof FullHttpRequest) {
                log.info("=========处理Http请求接入=========");
                handleHttpRequest(ctx, ((FullHttpRequest) msg));
            }
            // WebSocket接入 PooledUnsafeDirectByteBuf
            else if (msg instanceof WebSocketFrame) {
                log.info("=========处理websocket请求=========");
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            } else {
                log.info("=========其他类型=========");
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
        ctx.close();
        log.error("[WebSocketServerHandler]_出现异常 ,e : ", cause);
    }

    /**
     * 处理Http请求
     *
     * @param ctx
     * @param req
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        //Http解码失败，返回Http异常
        if (!req.getDecoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
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
            if (handshake.isSuccess()) {
                log.info("握手成功");
                userStatusManagerService.userConnectSuccessAfter(ImConstant.UserStatus.ON_LINE.getValue(), uidStr);
                //用户上线 此时需要处理离线消息【此时机主动push 10条最近的离线消息，后续依赖客户下拉获取也即pull 】
                Collection<String> lastOffLineMsgs = redissonUtils.getZSetRevRange(ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + uid, 0, imMsgConfig.getC2cMsgConfig().getPushOfflineMsgCount());
                if (!CollectionUtils.isEmpty(lastOffLineMsgs)) {
                    lastOffLineMsgs.forEach(msg -> {
                        try {
                            ctx.channel().writeAndFlush(new TextWebSocketFrame(msg));
                        } catch (Exception e) {
                            log.error("发送离线消息异常!:", e);
                        }
                    });
                }
            } else {
                log.warn("握手失败: {}", JSONUtil.toJsonStr(handshake));
            }
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
            handShaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        //判断是否为ping消息
        if (frame instanceof PingWebSocketFrame) {
            log.info("[WebSocketServerHandler]_消息类型: ping");
            NettyAttrUtil.updateReaderTime(ctx.channel(), System.currentTimeMillis());
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        //判断是否为文本消息
        if ((frame instanceof TextWebSocketFrame)) {
            log.info("[WebSocketServerHandler]_消息类型: 文本");
            String text = ((TextWebSocketFrame) frame).text();
            ImBaseRequest imBaseRequest = JSON.parseObject(text, ImBaseRequest.class);
            log.info("[WebSocketServerHandler]_消息原始数据: imBaseRequest:{}", JSONUtil.toJsonStr(imBaseRequest));
            //分发&处理消息，业务和netty线程隔离
            try {
                threadPoolTaskExecutor.execute(() -> {
                    handlerDispatcher.dispatcher(ctx, imBaseRequest);
                });
            } catch (Exception e) {
                log.error("[WebSocketServerHandler]_处理消息失败!,e:", e);
            }
        }

        if (frame instanceof BinaryWebSocketFrame) {
            //暂不适配该类型
            log.info("[WebSocketServerHandler]_消息类型: 二进制");
        }
    }

    private String getWebSocketLocation(FullHttpRequest req) {
        String location = req.headers().get(HttpHeaders.Names.HOST) + ImConstant.WEBSOCKET_PATH;
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
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
