package com.xzll.connect.netty.handler;


import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.dispatcher.HandlerDispatcher;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.netty.heart.HeartBeatHandler;
import com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl;

import com.xzll.connect.pojo.constant.Constant;
import com.xzll.connect.pojo.constant.UserRedisConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    private static final RedisTemplate<String, String> redisTemplate = SpringUtil.getBean("redisTemplate", RedisTemplate.class);
    private static final ThreadPoolTaskExecutor threadPoolTaskExecutor = SpringUtil.getBean(ThreadPoolTaskExecutor.class);

    //这里用来对连接数进行记数,每两秒输出到控制台
    private static final AtomicInteger nConnection = new AtomicInteger();

    static {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            System.out.println("连接数: " + nConnection.get());
        }, 0, 10, TimeUnit.SECONDS);
    }

    private WebSocketServerHandshaker handShaker;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
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
        //清楚用户登录信息
        redisTemplate.opsForHash().delete(UserRedisConstant.ROUTE_PREFIX, uid);
        //清除用户登录状态
        redisTemplate.opsForHash().delete(UserRedisConstant.LOGIN_STATUS_PREFIX, uid);
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
//        ThreadContext.put(TraceContexHolder.TRACE_ID, TraceContexHolder.buildTraceId(StringUtils.EMPTY));
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

        Object uid = ctx.channel().attr(AttributeKey.valueOf("userId")).get();
        if (null == uid) {
            log.warn("没有获取到uid");
            ctx.channel().close();
            return;
        }
        String uidStr = String.valueOf(uid);

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, false);
        handShaker = wsFactory.newHandshaker(req);
        if (handShaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            ChannelFuture handshake = handShaker.handshake(ctx.channel(), req);
            if (handshake.isSuccess()) {
                //TODO 用户登录的信息 <uid,机器ip端口> 存入redis
                //下边两步需要保证原子性 todo
                //设置当前用户登录的机器ip+端口  (设置用户登录的服务器信息 此处不设置过多信息 只设置用户登录的机器信息 方便快捷 存取无需转json )
                redisTemplate.opsForHash().put(UserRedisConstant.ROUTE_PREFIX, uidStr, NettyAttrUtil.getIpPortStr());
                //设置用户状态为在线
                redisTemplate.opsForHash().put(UserRedisConstant.LOGIN_STATUS_PREFIX, uidStr, UserRedisConstant.UserStatus.ON_LINE.getValue().toString());

                log.info("握手成功");
                //TODO 用户上线 此时需要处理离线消息
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
            MsgBaseRequest msgBaseRequest = JSON.parseObject(text, MsgBaseRequest.class);
            log.info("[WebSocketServerHandler]_消息原始数据: imBaseRequest:{}", JSONUtil.toJsonStr(msgBaseRequest));
            //分发&处理消息，业务和netty线程隔离
            try {
                threadPoolTaskExecutor.execute(() -> {
                    handlerDispatcher.dispatcher(ctx, msgBaseRequest);
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
        String location = req.headers().get(HttpHeaders.Names.HOST) + Constant.WEBSOCKET_PATH;
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
