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
            
            // 打印消息详细内容
            log.info("{}消息详情 - msgId: {}, from: {}, to: {}, format: {}, chatId: {}, time: {}, contentLength: {}", 
                TAG, req.getMsgId(), req.getFrom(), req.getTo(), req.getFormat(), 
                req.getChatId(), req.getTime(), req.getContent().length());
            C2CSendMsgAO packet = convertToAO(req);
            
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
            
            //3. 根据接收人状态做对应的处理
            if (null != targetChannel && Objects.equals(ImConstant.UserStatus.ON_LINE.getValue().toString(), userStatus)) {
                // 直接发送
                log.info("{}用户{}在线且在本台机器上,将直接发送", TAG, packet.getToUserId());
                sendProtoMsg(targetChannel, buildPushMsgResp(packet));
                
            } else if (null == userStatus && null == targetChannel) {
                log.info("{}用户{}不在线，将消息保存至离线表中", TAG, packet.getToUserId());
                // 发送mq消息，记录离线消息并更新db中消息状态为离线
                c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
                
            } else if (Objects.isNull(targetChannel) && Objects.equals(ImConstant.UserStatus.ON_LINE.toString(), userStatus)
                    && StringUtils.isNotBlank(ipPortStr)) {
                log.info("{}用户{}在线但是不在该机器上,跨服务器转发,目标服务器:{}", TAG, packet.getToUserId(), ipPortStr);
                
                // 【优化】通过 gRPC 跨服务器转发（直接传递 ImProtoRequest，最小化体积）
                String targetIp = NettyAttrUtil.getIpStr(ipPortStr);
                int targetPort = grpcClientConfig.getDefaultPort();
                try {
                    SmartGrpcClientManager.GrpcStubWrapper stubWrapper = grpcClientManager.getStubByIP(targetIp, targetPort);
                    MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(
                        stubWrapper.getChannelInfo().getChannel());
                    
                    // 构建 C2CSendReq
                    C2CSendReq c2cReq = C2CSendReq.newBuilder()
                        .setMsgId(packet.getMsgId())
                        .setFrom(packet.getFromUserId())
                        .setTo(packet.getToUserId())
                        .setFormat(packet.getMsgFormat())
                        .setContent(packet.getMsgContent())
                        .setTime(packet.getMsgCreateTime())
                        .setChatId(packet.getChatId())
                        .build();
                    
                    // 构建 ImProtoRequest（直接传递，无额外包装）
                    ImProtoRequest forwardRequest = ImProtoRequest.newBuilder()
                        .setType(MsgType.C2C_SEND)
                        .setPayload(com.google.protobuf.ByteString.copyFrom(c2cReq.toByteArray()))
                        .build();
                    
                    // 调用目标服务器的 transferC2CMsg 直接传递protobuf对象
                    com.xzll.grpc.WebBaseResponse response = stub.transferC2CMsg(forwardRequest);
                    log.info("{}通过gRPC转发消息结果: code={}, msg={}", TAG, response.getCode(), response.getMessage());
                    
                } catch (Exception e) {
                    log.error("{}gRPC转发消息失败: {}", TAG, e.getMessage(), e);
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
        log.debug("{}目标服务器接收并转发消息_开始", TAG);
        
        try {
            // 解析 C2CSendReq
            C2CSendReq req = C2CSendReq.parseFrom(protoRequest.getPayload());
            C2CSendMsgAO packet = convertToAO(req);
            
            // 获取本地接收人 Channel
            Channel targetChannel = LocalChannelManager.getChannelByUserId(packet.getToUserId());
            String userStatus = redissonUtils.getHash(ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, packet.getToUserId());
            
            // 二次校验接收人在线状态
            if (StringUtils.isNotBlank(userStatus) && null != targetChannel) {
                log.info("{}跳转后用户{}在线,将直接发送消息", TAG, packet.getToUserId());
                sendProtoMsg(targetChannel, buildPushMsgResp(packet));
            } else {
                log.info("{}跳转后用户{}不在线,将消息保存至离线表中", TAG, packet.getToUserId());
                // 发送mq消息，记录离线消息并更新db中消息状态为离线
                c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
            }
            
            log.debug("{}目标服务器接收并转发消息_结束", TAG);
            return WebBaseResponse.returnResultSuccess("跳转消息成功");
            
        } catch (InvalidProtocolBufferException e) {
            log.error("{}解析 protobuf 消息失败", TAG, e);
            return WebBaseResponse.returnResultError("解析消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 将 C2CSendReq 转换为 C2CSendMsgAO
     */
    private C2CSendMsgAO convertToAO(C2CSendReq req) {
        C2CSendMsgAO ao = new C2CSendMsgAO();
        ao.setMsgId(req.getMsgId());
        ao.setFromUserId(req.getFrom());
        ao.setToUserId(req.getTo());
        ao.setMsgFormat(req.getFormat());
        ao.setMsgContent(req.getContent());
        ao.setMsgCreateTime(req.getTime() > 0 ? req.getTime() : System.currentTimeMillis()); // 以服务器时间为准
        ao.setChatId(StringUtils.isNotBlank(req.getChatId()) ? req.getChatId() : 
            ChatIdUtils.buildC2CChatId(ImConstant.DEFAULT_BIZ_TYPE, Long.valueOf(req.getFrom()), Long.valueOf(req.getTo())));
        return ao;
    }
    
    /**
     * 构建推送消息响应
     */
    private C2CMsgPush buildPushMsgResp(C2CSendMsgAO packet) {
        return C2CMsgPush.newBuilder()
            .setMsgId(packet.getMsgId())
            .setFrom(packet.getFromUserId())
            .setTo(packet.getToUserId())
            .setFormat(packet.getMsgFormat())
            .setContent(packet.getMsgContent())
            .setTime(packet.getMsgCreateTime())
            .setChatId(packet.getChatId())
            .build();
    }
    
    /**
     * 发送 protobuf 消息
     */
    private void sendProtoMsg(Channel channel, C2CMsgPush pushMsg) {
        try {
            ImProtoResponse response = ImProtoResponse.newBuilder()
                .setType(MsgType.C2C_MSG_PUSH)
                .setPayload(com.google.protobuf.ByteString.copyFrom(pushMsg.toByteArray()))
                .setCode(ProtoResponseCode.SUCCESS)
                .build();
            
            byte[] bytes = response.toByteArray();
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            channel.writeAndFlush(new BinaryWebSocketFrame(buf));
            log.debug("{}发送 protobuf 消息成功", TAG);
        } catch (Exception e) {
            log.error("{}发送 protobuf 消息失败", TAG, e);
        }
    }
    
    /**
     * 更新消息为离线
     */
    private C2COffLineMsgAO buildOffLineMsgDTO(C2CSendMsgAO packet) {
        C2COffLineMsgAO build = C2COffLineMsgAO.builder()
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

