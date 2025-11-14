package com.xzll.connect.strategy.impl.c2c;

import com.google.protobuf.InvalidProtocolBufferException;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.ProtoResponseCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.util.ChatIdUtils;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.common.config.GrpcClientConfig;
import com.xzll.common.grpc.SmartGrpcClientManager;
import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
import com.xzll.connect.pojo.dto.ServerInfoDTO;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.ProtoMsgHandlerStrategy;
import com.xzll.grpc.C2CSendReq;
import com.xzll.grpc.ImProtoRequest;
import com.xzll.grpc.ImProtoResponse;
import com.xzll.grpc.MessageServiceGrpc;
import com.xzll.grpc.MsgType;
import com.xzll.grpc.C2CMsgPush;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;
import com.xzll.common.util.msgId.SnowflakeIdService;
import cn.hutool.extra.spring.SpringUtil;
import com.xzll.connect.netty.heart.HeartBeatHandler;
import com.xzll.connect.netty.heart.NettyServerHeartBeatHandlerImpl;

/**
 * @Author: hzz
 * @Date: 2022/1/14 15:20:10
 * @Description: 单聊消息，Protobuf 处理器
 */
@Slf4j
@Service
public class C2CMsgSendProtoStrategyImpl extends MsgHandlerCommonAbstract implements ProtoMsgHandlerStrategy {

    private static final String TAG = "[Protobuf客户端发送单聊消息]_";

    @Resource
    private RedissonUtils redissonUtils;
    @Lazy
    @Resource
    private C2CMsgProvider c2CMsgProvider;
    @Resource
    private SmartGrpcClientManager grpcClientManager;
    @Resource
    private GrpcClientConfig grpcClientConfig;
    @Resource
    private SnowflakeIdService snowflakeIdService;

    @Override
    public MsgType supportMsgType() {
        return MsgType.C2C_SEND;
    }

    /**
     * 处理客户端直连的 C2C 消息（对应原有的 exchange 方法）
     * 
     * 职责：
     * 1. 保存消息到数据库
     * 2. 查找接收人并推送/转发
     */
    @Override
    public void exchange(ChannelHandlerContext ctx, ImProtoRequest protoRequest) {
        log.debug("{}客户端发送单聊消息_开始", TAG);
        
        try {
            // 打印 ImProtoRequest 详细信息
            log.info("{}收到客户端消息 - 消息类型: {}, Payload大小: {} bytes", 
                TAG, protoRequest.getType(), protoRequest.getPayload().size());
            
            // 解析 C2CSendReq
            C2CSendReq req = C2CSendReq.parseFrom(protoRequest.getPayload());
            
            // 打印消息详细内容（优化后：chatId已删除，ID改为long）
            log.debug("{}【步骤1-接收消息】clientMsgId(UUID bytes): {}, msgId: {}, from: {}, to: {}, format: {}, time: {}, contentLength: {}",
                TAG, ProtoConverterUtil.bytesToUuidString(req.getClientMsgId()), req.getMsgId(), req.getFrom(), req.getTo(), req.getFormat(), 
                req.getTime(), req.getContent().length());
            
            C2CSendMsgAO packet = convertToAO(req);
            
            // 打印转换后的AO对象信息
            log.debug("{}【步骤2-转换完成】转换后AO - clientMsgId: {}, msgId: {}, fromUserId: {}, toUserId: {}, chatId: {}",
                TAG, packet.getClientMsgId(), packet.getMsgId(), packet.getFromUserId(), packet.getToUserId(), packet.getChatId());
            
            //1. 更新会话记录并保存消息记录
            c2CMsgProvider.sendC2CMsg(packet);
            
            //2. 获取接收人登录，服务信息，根据状态进行处理
            ReceiveUserDataDTO receiveUserData = super.getReceiveUserDataTemplate(
                packet.getToUserId(), this.redissonUtils);
            
            String channelIdByUserId = receiveUserData.getChannelIdByUserId();
            Channel targetChannel = receiveUserData.getTargetChannel();
            String ipPortStr = receiveUserData.getRouteAddress();
            String userStatus = receiveUserData.getUserStatus();
            ServerInfoDTO serverInfoDTO = receiveUserData.getServerInfoDTO();
            log.info("{}接收者id:{},在线状态:{},channelId:{},serverInfo:{}", 
                TAG, packet.getToUserId(), userStatus, channelIdByUserId, serverInfoDTO);
            
            // ✅ 心跳异常检测：如果接收人心跳ping异常，第一时间感知，将消息改为离线消息
            if (null != targetChannel && Objects.equals(ImConstant.UserStatus.ON_LINE.getValue().toString(), userStatus)) {
                if (isUserHeartbeatAbnormal(targetChannel, channelIdByUserId, packet.getToUserId())) {
                    // 心跳异常，保存为离线消息
                    log.warn("{}【步骤3-心跳异常检测】用户{}心跳异常，将消息保存为离线消息 - clientMsgId: {}, msgId: {}, channelId: {}",
                        TAG, packet.getToUserId(), packet.getClientMsgId(), packet.getMsgId(), channelIdByUserId);
                    c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
                    return; // 直接返回，不再推送
                }
            }
            
            //3. 根据接收人状态做对应的处理
            if (null != targetChannel && Objects.equals(ImConstant.UserStatus.ON_LINE.getValue().toString(), userStatus)) {
                // 直接发送
                log.debug("{}【步骤3-本地发送】用户{}在线且在本台机器上,将直接发送 - clientMsgId: {}, msgId: {}",
                    TAG, packet.getToUserId(), packet.getClientMsgId(), packet.getMsgId());
                sendProtoMsg(targetChannel, buildPushMsgResp(packet));
                
            } else if (null == userStatus && null == targetChannel) {
                log.debug("{}【步骤3-离线处理】用户{}不在线，将消息保存至离线表中 - clientMsgId: {}, msgId: {}",
                    TAG, packet.getToUserId(), packet.getClientMsgId(), packet.getMsgId());
                // 发送mq消息，记录离线消息并更新db中消息状态为离线
                c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
                
            } else if (Objects.isNull(targetChannel) && Objects.equals(ImConstant.UserStatus.ON_LINE.toString(), userStatus)
                    && StringUtils.isNotBlank(ipPortStr)) {
                log.debug("{}【步骤3-跨服务器转发】用户{}在线但是不在该机器上,跨服务器转发,目标服务器:{} - clientMsgId: {}, msgId: {}",
                    TAG, packet.getToUserId(), ipPortStr, packet.getClientMsgId(), packet.getMsgId());
                
                // 【优化】通过 gRPC 跨服务器转发（直接传递 ImProtoRequest，最小化体积）
                String targetIp = NettyAttrUtil.getIpStr(ipPortStr);
                int targetPort = grpcClientConfig.getDefaultPort();
                try {
                    SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStubByIP(targetIp, targetPort);
                    MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(
                        stubWrapper.getChannelInfo().getChannel());
                    
                    // 构建 C2CSendReq（优化后：使用fixed64和bytes，chatId已删除）
                    C2CSendReq c2cReq = C2CSendReq.newBuilder()
                        .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(packet.getClientMsgId())) // UUID -> bytes
                        .setMsgId(ProtoConverterUtil.snowflakeStringToLong(packet.getMsgId())) // string -> fixed64
                        .setFrom(ProtoConverterUtil.snowflakeStringToLong(packet.getFromUserId())) // string -> fixed64
                        .setTo(ProtoConverterUtil.snowflakeStringToLong(packet.getToUserId())) // string -> fixed64
                        .setFormat(packet.getMsgFormat())
                        .setContent(packet.getMsgContent())
                        .setTime(packet.getMsgCreateTime())
                        // chatId 已删除，服务端根据from+to动态拼接
                        .build();
                    
                    log.info("{}【跨服务器转发-构建请求】目标IP: {}, 转发数据 - clientMsgId: {}, msgId: {}, from: {}, to: {}", 
                        TAG, targetIp, packet.getClientMsgId(), packet.getMsgId(), packet.getFromUserId(), packet.getToUserId());
                    
                    // 构建 ImProtoRequest（直接传递，无额外包装）
                    ImProtoRequest forwardRequest = ImProtoRequest.newBuilder()
                        .setType(MsgType.C2C_SEND)
                        .setPayload(com.google.protobuf.ByteString.copyFrom(c2cReq.toByteArray()))
                        .build();
                    
                    // 调用目标服务器的 transferC2CMsg 直接传递protobuf对象
                    com.xzll.grpc.WebBaseResponse response = stub.transferC2CMsg(forwardRequest);
                    log.info("{}【跨服务器转发-结果】gRPC转发消息结果: code={}, msg={} - clientMsgId: {}, msgId: {}", 
                        TAG, response.getCode(), response.getMessage(), packet.getClientMsgId(), packet.getMsgId());
                    
                } catch (Exception e) {
                    log.error("{}【跨服务器转发-异常】gRPC转发消息失败 - clientMsgId: {}, msgId: {}, error: {}", 
                        TAG, packet.getClientMsgId(), packet.getMsgId(), e.getMessage(), e);
                }
            }
            
            log.debug("{}客户端发送单聊消息_结束", TAG);
        } catch (InvalidProtocolBufferException e) {
            log.error("{}解析 protobuf 消息失败", TAG, e);
        }
    }
    
    /**
     * 接收并转发跨服务器的 C2C 消息
     * 
     * 职责：
     * 1. 不保存消息（避免重复，消息已在源服务器保存）
     * 2. 二次校验接收人在线状态
     * 3. 直接推送给本地客户端
     */
    @Override
    public WebBaseResponse receiveAndSendMsg(ImProtoRequest protoRequest) {
        log.debug("{}【receiveAndSendMsg开始】目标服务器接收并转发消息", TAG);
        
        try {
            // 解析 C2CSendReq
            C2CSendReq req = C2CSendReq.parseFrom(protoRequest.getPayload());
            log.info("{}【receiveAndSendMsg-解析】接收到转发消息 - clientMsgId: {}, msgId: {}, from: {}, to: {}", 
                TAG, ProtoConverterUtil.bytesToUuidString(req.getClientMsgId()), req.getMsgId(), req.getFrom(), req.getTo());
            
            C2CSendMsgAO packet = convertToAO(req);
            
            // 获取本地接收人 Channel
            Channel targetChannel = LocalChannelManager.getChannelByUserId(packet.getToUserId());
            String userStatus = redissonUtils.getHash(ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, packet.getToUserId());
            
            log.debug("{}【receiveAndSendMsg-状态检查】用户: {}, 在线状态: {}, 本地Channel: {} - clientMsgId: {}, msgId: {}",
                TAG, packet.getToUserId(), userStatus, (targetChannel != null ? "存在" : "不存在"), 
                packet.getClientMsgId(), packet.getMsgId());
            
            // 二次校验接收人在线状态
            if (StringUtils.isNotBlank(userStatus) && null != targetChannel) {
                // ✅ 心跳异常检测：如果接收人心跳ping异常，第一时间感知，将消息改为离线消息
                String channelId = targetChannel.id().asLongText();
                if (isUserHeartbeatAbnormal(targetChannel, channelId, packet.getToUserId())) {
                    // 心跳异常，保存为离线消息
                    log.warn("{}【receiveAndSendMsg-心跳异常检测】用户{}心跳异常，将消息保存为离线消息 - clientMsgId: {}, msgId: {}, channelId: {}",
                        TAG, packet.getToUserId(), packet.getClientMsgId(), packet.getMsgId(), channelId);
                    c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
                    return WebBaseResponse.returnResultSuccess("消息已保存为离线消息（心跳异常）");
                }
                
                log.debug("{}【receiveAndSendMsg-本地发送】跳转后用户{}在线,将直接发送消息 - clientMsgId: {}, msgId: {}",
                    TAG, packet.getToUserId(), packet.getClientMsgId(), packet.getMsgId());
                sendProtoMsg(targetChannel, buildPushMsgResp(packet));
            } else {
                log.debug("{}【receiveAndSendMsg-离线处理】跳转后用户{}不在线,将消息保存至离线表中 - clientMsgId: {}, msgId: {}",
                    TAG, packet.getToUserId(), packet.getClientMsgId(), packet.getMsgId());
                // 发送mq消息，记录离线消息并更新db中消息状态为离线
                c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
            }
            
            log.info("{}【receiveAndSendMsg完成】目标服务器接收并转发消息成功 - clientMsgId: {}, msgId: {}", 
                TAG, packet.getClientMsgId(), packet.getMsgId());
            return WebBaseResponse.returnResultSuccess("跳转消息成功");
            
        } catch (InvalidProtocolBufferException e) {
            log.error("{}【receiveAndSendMsg-异常】解析 protobuf 消息失败", TAG, e);
            return WebBaseResponse.returnResultError("解析消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 将 C2CSendReq 转换为 C2CSendMsgAO（优化后：适配fixed64和bytes）
     */
    private C2CSendMsgAO convertToAO(C2CSendReq req) {
        C2CSendMsgAO ao = new C2CSendMsgAO();
        
        // UUID bytes -> string
        ao.setClientMsgId(ProtoConverterUtil.bytesToUuidString(req.getClientMsgId()));
        
        // fixed64 -> string（如果客户端传了msgId则使用，否则服务端生成）
        String msgId;
        if (req.getMsgId() > 0) {
            msgId = ProtoConverterUtil.longToSnowflakeString(req.getMsgId());
            log.debug("{}使用客户端传递的msgId: {}", TAG, msgId);
        } else {
            msgId = snowflakeIdService.generateSimpleMessageId();
            log.info("{}客户端msgId为空，服务端生成新msgId: {}", TAG, msgId);
        }
        ao.setMsgId(msgId);
        
        // fixed64 -> string
        ao.setFromUserId(ProtoConverterUtil.longToSnowflakeString(req.getFrom()));
        ao.setToUserId(ProtoConverterUtil.longToSnowflakeString(req.getTo()));
        ao.setMsgFormat(req.getFormat());
        ao.setMsgContent(req.getContent());
        ao.setMsgCreateTime(req.getTime() > 0 ? req.getTime() : System.currentTimeMillis());
        
        // chatId 在proto中已删除，服务端根据from+to动态生成
        ao.setChatId(ChatIdUtils.buildC2CChatId(ImConstant.DEFAULT_BIZ_TYPE, req.getFrom(), req.getTo()));
        
        return ao;
    }
    
    /**
     * 构建推送消息响应（优化后：使用fixed64和bytes，chatId已删除）
     */
    private C2CMsgPush buildPushMsgResp(C2CSendMsgAO packet) {
        return C2CMsgPush.newBuilder()
            .setClientMsgId(ProtoConverterUtil.uuidStringToBytes(packet.getClientMsgId())) // string UUID -> bytes
            .setMsgId(ProtoConverterUtil.snowflakeStringToLong(packet.getMsgId())) // string -> fixed64
            .setFrom(ProtoConverterUtil.snowflakeStringToLong(packet.getFromUserId())) // string -> fixed64
            .setTo(ProtoConverterUtil.snowflakeStringToLong(packet.getToUserId())) // string -> fixed64
            .setFormat(packet.getMsgFormat())
            .setContent(packet.getMsgContent())
            .setTime(packet.getMsgCreateTime())
            // chatId 已删除，客户端根据from+to动态拼接
            .build();
    }
    
    /**
     * 发送 protobuf 消息
     */
    private void sendProtoMsg(Channel channel, C2CMsgPush pushMsg) {
        try {
            log.debug("{}【sendProtoMsg】开始发送消息到客户端 - clientMsgId(bytes): {}, msgId: {}, to: {}",
                TAG, ProtoConverterUtil.bytesToUuidString(pushMsg.getClientMsgId()), pushMsg.getMsgId(), pushMsg.getTo());
            
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(MsgType.C2C_MSG_PUSH)
                .setPayload(com.google.protobuf.ByteString.copyFrom(pushMsg.toByteArray()))
                .setCode(ProtoResponseCode.SUCCESS)
                .build();
            
            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            channel.writeAndFlush(new BinaryWebSocketFrame(buf));
            
            log.debug("{}【sendProtoMsg成功】消息发送到客户端成功 - clientMsgId(bytes): {}, msgId: {}, to: {}, payloadSize: {} bytes",
                TAG, ProtoConverterUtil.bytesToUuidString(pushMsg.getClientMsgId()), pushMsg.getMsgId(), pushMsg.getTo(), bytes.length);
        } catch (Exception e) {
            log.error("{}【sendProtoMsg异常】发送 protobuf 消息失败 - clientMsgId(bytes): {}, msgId: {}, to: {}, error: {}", 
                TAG, ProtoConverterUtil.bytesToUuidString(pushMsg.getClientMsgId()), pushMsg.getMsgId(), pushMsg.getTo(), e.getMessage(), e);
        }
    }
    
    /**
     * 检查用户心跳是否异常
     * 如果心跳失败次数 > 0，认为用户可能已断网（服务端在120s之后断网清理连接），此时消息应保存为离线消息，待客户端自动重连后  给其推送离线消息
     * 
     * @param channel 用户连接Channel
     * @param channelId 连接ID
     * @param userId 用户ID
     * @return true表示心跳异常，false表示心跳正常
     */
    private boolean isUserHeartbeatAbnormal(Channel channel, String channelId, String userId) {
        if (channel == null || !channel.isActive()) {
            return true; // 连接不存在或不活跃，认为异常
        }
        
        if (StringUtils.isBlank(channelId)) {
            return true; // 没有channelId，无法检查，认为不正常
        }
        
        try {
            // 获取心跳处理器
            HeartBeatHandler heartBeatHandler = SpringUtil.getBean(HeartBeatHandler.class);
            if (heartBeatHandler instanceof NettyServerHeartBeatHandlerImpl) {
                NettyServerHeartBeatHandlerImpl heartbeatHandler = 
                    (NettyServerHeartBeatHandlerImpl) heartBeatHandler;
                
                // 获取心跳失败次数
                int failureCount = heartbeatHandler.getHeartbeatFailureCount(channelId);
                
                if (failureCount > 0) {
                    log.warn("{}【心跳异常检测】用户{}心跳失败次数={}，可能已断网 - channelId: {}", 
                        TAG, userId, failureCount, channelId);
                    return true; // 心跳失败次数 > 0，认为异常
                }
            }
        } catch (Exception e) {
            log.warn("{}【心跳异常检测】检查用户{}心跳状态异常 - channelId: {}, error: {}", 
                TAG, userId, channelId, e.getMessage());
            // 检查异常时，不认为心跳异常，避免误判
        }
        
        return false; // 心跳正常
    }
    
    /**
     * 更新消息为离线
     */
    private C2COffLineMsgAO buildOffLineMsgDTO(C2CSendMsgAO packet) {
        C2COffLineMsgAO build = C2COffLineMsgAO.builder()
            .clientMsgId(packet.getClientMsgId()) // 双轨制：传递客户端消息ID
            .fromUserId(packet.getFromUserId())
            .toUserId(packet.getToUserId())
            .msgStatus(MsgStatusEnum.MsgStatus.OFF_LINE.getCode())
            .msgContent(packet.getMsgContent())
            .msgFormat(packet.getMsgFormat())
            .build();
        build.setMsgId(packet.getMsgId());
        build.setChatId(packet.getChatId());
        build.setMsgCreateTime(packet.getMsgCreateTime());
        return build;
    }
}

