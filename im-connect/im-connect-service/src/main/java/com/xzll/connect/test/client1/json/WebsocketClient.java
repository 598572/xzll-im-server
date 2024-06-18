package com.xzll.connect.test.client1.json;



import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;

import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:32:01
 * @Description:
 */

@Slf4j
public class WebsocketClient {


    public static final String VALUE = "111";
    private static EventLoopGroup group = new NioEventLoopGroup();

    private String ip;
    private int port;
    private String uriStr;
    private static WebsocketClientHandler handler;

    public WebsocketClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        uriStr = "ws://" + ip + ":" + port + "/websocket";
    }

    public void run() throws InterruptedException, URISyntaxException {
        // 主要是为handler(自己写的类)服务，用于初始化EasyWsHandle
        URI wsUri = new URI(uriStr);
        DefaultHttpHeaders entries = new DefaultHttpHeaders();
        entries.set("token", "t_value");
        entries.set("uid", VALUE);


        // 设置Bootstrap
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class).attr(ImConstant.USER_ID_KEY, VALUE);

        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory
                .newHandshaker(wsUri, WebSocketVersion.V13, null, true, entries, 100 * 1024 * 1024);
        handler = new WebsocketClientHandler(webSocketClientHandshaker);

        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
//                ch.attr()

//                ch.attr( AttributeKey.valueOf(USER_ID)).set(RandomUtil.randomNumbers(2));
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new LoggingHandler(LogLevel.INFO));

                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast("heart-notice", new IdleStateHandler(11, 0, 0, TimeUnit.SECONDS));
                pipeline.addLast(handler);


//                pipeline.addLast(new MsgEncoder());
                // 解码器
//                pipeline.addLast(new MsgDecoder());
                //解码器，通过Google Protocol Buffers序列化框架动态的切割接收到的ByteBuf
//                pipeline.addLast(new ProtobufVarint32FrameDecoder());
                //Google Protocol Buffers 长度属性编码器
//                pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                // 自定义数据接收处理器

                // Protobuf消息解码器
//                pipeline.addLast(new ProtobufDecoder(IMResponseProto.IMResProtocol.getDefaultInstance()));
//
//                pipeline.addLast(new MessageToMessageDecoder<WebSocketFrame>() {
//                    @Override
//                    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame,
//                                          List<Object> objs) throws Exception {
//                        log.info("MessageToMessageDecoder msg ------------------------");
//                        if (frame instanceof TextWebSocketFrame) {
//                            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
//                            log.info("TextWebSocketFrame");
//                        } else if (frame instanceof BinaryWebSocketFrame) {
//                            ByteBuf buf = ((BinaryWebSocketFrame) frame).content();
//                            objs.add(buf);
//                            buf.retain();
//                            log.info("BinaryWebSocketFrame received------------------------");
//                        } else if (frame instanceof PongWebSocketFrame) {
//                            log.info("WebSocket Client received pong");
//                        } else if (frame instanceof CloseWebSocketFrame) {
//                            log.info("receive close frame");
//                        }
//
//                    }
//                });

//                pipeline.addLast(new MessageToMessageEncoder<MessageLiteOrBuilder>() {
//                    @Override
//                    protected void encode(ChannelHandlerContext ctx, MessageLiteOrBuilder msg, List<Object> out) throws Exception {
//                        ByteBuf result = null;
//                        if (msg instanceof MessageLite) {
//                            // 没有build的Protobuf消息
//                            result = wrappedBuffer(((MessageLite) msg).toByteArray());
//                        }
//                        if (msg instanceof MessageLite.Builder) {
//                            // 经过build的Protobuf消息
//                            result = wrappedBuffer(((MessageLite.Builder) msg).build().toByteArray());
//                        }
//                        // 将Protbuf消息包装成Binary Frame 消息
//                        WebSocketFrame frame = new BinaryWebSocketFrame(result);
//                        out.add(frame);
//                    }
//                });


            }
        });


        // 连接服务端
        //登录时传过来uid
        ChannelFuture channelFuture = bootstrap.connect(ip, port).sync();

        handler.handshakeFuture().sync();

        Scanner sc = new Scanner(System.in);


        new Thread(() -> {
            while (true) {
                String s = sc.nextLine();
                if (!StringUtils.isEmpty(s)) {

//                    IMRequestProtobuf.IMReqProtocol protocol = IMRequestProtobuf.IMReqProtocol.newBuilder()
//                            .setVersion(1)
//                            .setMsgId(RandomUtil.getRandom())
//                            .setMsgType(2)
//                            .setMsgContent(s)
//                            .setFromId(123455)
//                            .setToId(100)
//                            .setMsgCreateTime(System.currentTimeMillis())
//                            .build();

//                    String s1 = JsonUtil.toJson(protocol);
//                    System.out.println(s1);
//                    int length = s1.getBytes(StandardCharsets.UTF_8).length;
//                    System.out.println("json的长度："+length);
//
//                    System.out.println("protobuf的长度："+protocol.toByteArray().length);
//                    ByteBuf byteBuf1 = wrappedBuffer(protocol.toByteArray());//Unpooled.copiedBuffer(protocol.toByteArray())


                    MsgBaseRequest<C2CMsgRequestDTO> msgBaseRequest = new MsgBaseRequest<C2CMsgRequestDTO>();
                    MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
                    msgType.setFirstLevelMsgType(3);
                    msgType.setSecondLevelMsgType(301);
                    msgBaseRequest.setMsgType(msgType);
                    C2CMsgRequestDTO c2CMsgRequestDTO = new C2CMsgRequestDTO();
                    c2CMsgRequestDTO.setMsgId(UUID.randomUUID().toString());
                    c2CMsgRequestDTO.setMsgContent(s);
                    c2CMsgRequestDTO.setChatId("23434344");
                    c2CMsgRequestDTO.setToUserId("222");
                    c2CMsgRequestDTO.setFromUserId("111");
                    c2CMsgRequestDTO.setMsgCreateTime(System.currentTimeMillis());
                    msgBaseRequest.setBody(c2CMsgRequestDTO);

                    TextWebSocketFrame textFrame = new TextWebSocketFrame(JSONUtil.toJsonStr(msgBaseRequest));
                    //文本消息
                    channelFuture.channel().writeAndFlush(textFrame);
                    channelFuture.addListener((ChannelFutureListener) lis ->
                            log.info("客户端手动发消息成功={}", textFrame.text()));
                }
            }
        }).start();

        // 堵塞线程，保持长连接
        channelFuture.channel().closeFuture().sync();
    }

}
