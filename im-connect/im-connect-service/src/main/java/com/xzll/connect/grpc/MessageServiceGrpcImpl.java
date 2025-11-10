package com.xzll.connect.grpc;

import cn.hutool.core.lang.Assert;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.ProtoResponseCode;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.connect.service.TransferC2CMsgService;
import io.grpc.stub.StreamObserver;
import io.netty.channel.Channel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;

import com.xzll.grpc.ImProtoResponse;
import com.xzll.grpc.MsgType;
import com.xzll.grpc.C2CAckReq;
import com.xzll.grpc.C2CWithdrawReq;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: gRPC消息服务实现，替换原来的Dubbo服务
 */
@Service
@Slf4j
public class MessageServiceGrpcImpl extends com.xzll.grpc.MessageServiceGrpc.MessageServiceImplBase {

    @Resource
    private TransferC2CMsgService transferC2CMsgService;
    
    @Resource
    private RedissonUtils redissonUtils;

    @Override
    public void responseServerAck2Client(com.xzll.grpc.ServerAckPush request, 
                                       StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            Assert.isTrue(Objects.nonNull(request), "参数错误");
            Assert.isTrue(StringUtils.isNotBlank(request.getToUserId()), "发送服务端ack时缺少必填参数");
            
            // 按新协议：构建 C2CAckReq，type=C2C_ACK，通过二进制帧下发
            // 双轨制：包含 clientMsgId 和 serverMsgId
            C2CAckReq ackReq = C2CAckReq.newBuilder()
                    .setClientMsgId(request.getClientMsgId()) //  客户端消息ID
                    .setMsgId(request.getMsgId()) // 服务端消息ID
                    .setFrom("")
                    .setTo(request.getToUserId())
                    .setStatus(request.getMsgReceivedStatus())
                    .setChatId(request.getChatId())
                    .build();

            ImProtoResponse response = ImProtoResponse.newBuilder()
                    .setType(MsgType.C2C_ACK)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(ackReq.toByteArray()))
                    .setCode(ProtoResponseCode.SUCCESS)
                    .build();

            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            boolean result = this.sendProtoToClient(targetChannel, response);
            
            AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
            com.xzll.grpc.WebBaseResponse grpcResponse = buildGrpcResponse(resultAnswer);
            
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
            
            log.info("gRPC响应服务端ack成功，用户: {}, 结果: {}", request.getToUserId(), result);
            
        } catch (Exception e) {
            log.error("gRPC响应服务端ack异常:", e);
            com.xzll.grpc.WebBaseResponse errorResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(AnswerCode.ERROR.getCode())
                    .setMessage("处理失败: " + e.getMessage())
                    .setSuccess(false)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void responseClientAck2Client(com.xzll.grpc.ClientAckPush request, 
                                       StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            Assert.isTrue(Objects.nonNull(request), "参数错误");
            Assert.isTrue(StringUtils.isNotBlank(request.getToUserId()), "发送客户端ack时缺少必填参数");
            
            // 按新协议：构建 C2CAckReq（未读/已读），type=C2C_ACK，通过二进制帧下发
            // 双轨制：包含 clientMsgId 和 serverMsgId
            C2CAckReq ackReq = C2CAckReq.newBuilder()
                    .setClientMsgId(request.getClientMsgId()) // ✅ 客户端消息ID
                    .setMsgId(request.getMsgId()) // ✅ 服务端消息ID
                    .setFrom("")
                    .setTo(request.getToUserId())
                    .setStatus(request.getMsgReceivedStatus())
                    .setChatId(request.getChatId())
                    .build();

            ImProtoResponse response = ImProtoResponse.newBuilder()
                    .setType(MsgType.C2C_ACK)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(ackReq.toByteArray()))
                    .setCode(ProtoResponseCode.SUCCESS)
                    .build();

            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            boolean result = this.sendProtoToClient(targetChannel, response);
            
            AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
            com.xzll.grpc.WebBaseResponse grpcResponse = buildGrpcResponse(resultAnswer);
            
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
            
            log.info("gRPC响应客户端ack成功，用户: {}, 结果: {}", request.getToUserId(), result);
            
        } catch (Exception e) {
            log.error("gRPC响应客户端ack异常:", e);
            com.xzll.grpc.WebBaseResponse errorResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(AnswerCode.ERROR.getCode())
                    .setMessage("处理失败: " + e.getMessage())
                    .setSuccess(false)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendWithdrawMsg2Client(com.xzll.grpc.WithdrawPush request, 
                                     StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            Assert.isTrue(Objects.nonNull(request), "参数错误");
            
            // 按新协议：构建 C2CWithdrawReq，type=C2C_WITHDRAW，通过二进制帧下发
            C2CWithdrawReq withdrawReq = C2CWithdrawReq.newBuilder()
                    .setMsgId(request.getMsgId())
                    .setFrom(request.getFromUserId())
                    .setTo(request.getToUserId())
                    .setChatId(request.getChatId())
                    .build();

            ImProtoResponse response = ImProtoResponse.newBuilder()
                    .setType(MsgType.C2C_WITHDRAW)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(withdrawReq.toByteArray()))
                    .setCode(ProtoResponseCode.SUCCESS)
                    .build();

            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            boolean result = this.sendProtoToClient(targetChannel, response);
            
            AnswerCode resultAnswer = result ? AnswerCode.SUCCESS : AnswerCode.ERROR;
            com.xzll.grpc.WebBaseResponse grpcResponse = buildGrpcResponse(resultAnswer);
            
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
            
            log.info("gRPC发送撤回消息成功，用户: {}, 结果: {}", request.getToUserId(), result);
            
        } catch (Exception e) {
            log.error("gRPC发送撤回消息异常:", e);
            com.xzll.grpc.WebBaseResponse errorResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(AnswerCode.ERROR.getCode())
                    .setMessage("处理失败: " + e.getMessage())
                    .setSuccess(false)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void transferC2CMsg(com.xzll.grpc.ImProtoRequest request, 
                              StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            // 【优化】直接传递 ImProtoRequest，无需额外包装
            // 体积最小、传输最快、带宽占用最小、序列化最快
            
            // 调用转发服务
            WebBaseResponse result = transferC2CMsgService.transferC2CMsg(request);
            
            com.xzll.grpc.WebBaseResponse grpcResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMsg())
                    .setData(result.getData() != null ? result.getData().toString() : "")
                    .setSuccess(result.getCode() == AnswerCode.SUCCESS.getCode())
                    .build();
            
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
            
            log.info("gRPC跨服务器转发Protobuf消息成功, msgType: {}, 响应: {}", 
                request.getType(), result.getMsg());
            
        } catch (Exception e) {
            log.error("gRPC跨服务器转发Protobuf消息异常:", e);
            com.xzll.grpc.WebBaseResponse errorResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(AnswerCode.ERROR.getCode())
                    .setMessage("处理失败: " + e.getMessage())
                    .setSuccess(false)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }


    /**
     * 发送消息到客户端
     */
    private boolean sendProtoToClient(Channel channel, ImProtoResponse response) {
        try {
            if (Objects.nonNull(channel)) {
                byte[] bytes = response.toByteArray();
                ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                channel.writeAndFlush(new BinaryWebSocketFrame(buf));
                return true;
            }
            log.error("服务端发送protobuf_传入的channel为空，不发送!");
        } catch (Exception e) {
            log.error("服务端发送protobuf_异常:", e);
        }
        return false;
    }

    /**
     * 推送好友请求到客户端
     */
    @Override
    public void pushFriendRequest2Client(com.xzll.grpc.FriendRequestPush request,
                                         StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            log.info("gRPC接收好友请求推送: toUserId={}, fromUserId={}, requestId={}", 
                    request.getToUserId(), request.getFromUserId(), request.getRequestId());

            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            
            // 检查用户是否在线
            if (targetChannel == null) {
                log.warn("目标用户不在线，将好友请求保存为离线推送: toUserId={}, requestId={}", 
                        request.getToUserId(), request.getRequestId());
                
                // 将好友请求保存到 Redis，待用户上线后推送
                try {
                    String offlineKey = ImConstant.RedisKeyConstant.OFF_LINE_FRIEND_REQUEST_KEY + request.getToUserId();
                    
                    // 构建 ImProtoResponse 并序列化为字节数组
                    ImProtoResponse response = ImProtoResponse.newBuilder()
                            .setType(MsgType.FRIEND_REQUEST)  // 好友请求消息类型
                            .setPayload(com.google.protobuf.ByteString.copyFrom(request.toByteArray()))
                            .setCode(ProtoResponseCode.SUCCESS)
                            .build();
                    
                    // 使用 ZSet 存储，score 使用时间戳，便于排序
                    redissonUtils.addZSet(
                            offlineKey, 
                            java.util.Base64.getEncoder().encodeToString(response.toByteArray()),
                            System.currentTimeMillis()
                    );
                    
                    log.info("好友请求已保存为离线推送: toUserId={}, requestId={}", 
                            request.getToUserId(), request.getRequestId());
                    responseObserver.onNext(buildGrpcResponse(AnswerCode.SUCCESS));
                } catch (Exception e) {
                    log.error("保存离线好友请求失败: toUserId={}, requestId={}", 
                            request.getToUserId(), request.getRequestId(), e);
                    responseObserver.onNext(buildGrpcResponse(AnswerCode.ERROR));
                }
                responseObserver.onCompleted();
                return;
            }

            // 用户在线，直接推送
            ImProtoResponse response = ImProtoResponse.newBuilder()
                    .setType(MsgType.FRIEND_REQUEST)  // 好友请求消息类型
                    .setPayload(com.google.protobuf.ByteString.copyFrom(request.toByteArray()))
                    .setCode(ProtoResponseCode.SUCCESS)
                    .build();

            boolean success = sendProtoToClient(targetChannel, response);
            
            if (success) {
                log.info("好友请求推送成功: toUserId={}, requestId={}", request.getToUserId(), request.getRequestId());
                responseObserver.onNext(buildGrpcResponse(AnswerCode.SUCCESS));
            } else {
                log.error("好友请求推送失败: toUserId={}, requestId={}", request.getToUserId(), request.getRequestId());
                responseObserver.onNext(buildGrpcResponse(AnswerCode.ERROR));
            }
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("gRPC推送好友请求异常:", e);
            com.xzll.grpc.WebBaseResponse errorResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(AnswerCode.ERROR.getCode())
                    .setMessage("处理失败: " + e.getMessage())
                    .setSuccess(false)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * 推送好友响应到客户端
     */
    @Override
    public void pushFriendResponse2Client(com.xzll.grpc.FriendResponsePush request,
                                          StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            log.info("gRPC接收好友响应推送: toUserId={}, fromUserId={}, requestId={}, status={}", 
                    request.getToUserId(), request.getFromUserId(), request.getRequestId(), request.getStatus());

            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            
            // 检查用户是否在线
            if (targetChannel == null) {
                log.warn("目标用户不在线，将好友响应保存为离线推送: toUserId={}, requestId={}", 
                        request.getToUserId(), request.getRequestId());
                
                // 将好友响应保存到 Redis，待用户上线后推送
                try {
                    String offlineKey = ImConstant.RedisKeyConstant.OFF_LINE_FRIEND_RESPONSE_KEY + request.getToUserId();
                    
                    // 构建 ImProtoResponse 并序列化为字节数组
                    ImProtoResponse response = ImProtoResponse.newBuilder()
                            .setType(MsgType.FRIEND_RESPONSE)  // 好友响应消息类型
                            .setPayload(com.google.protobuf.ByteString.copyFrom(request.toByteArray()))
                            .setCode(ProtoResponseCode.SUCCESS)
                            .build();
                    
                    // 使用 ZSet 存储，score 使用时间戳，便于排序
                    redissonUtils.addZSet(
                            offlineKey, 
                            java.util.Base64.getEncoder().encodeToString(response.toByteArray()),
                            System.currentTimeMillis()
                    );
                    
                    log.info("好友响应已保存为离线推送: toUserId={}, requestId={}", 
                            request.getToUserId(), request.getRequestId());
                    responseObserver.onNext(buildGrpcResponse(AnswerCode.SUCCESS));
                } catch (Exception e) {
                    log.error("保存离线好友响应失败: toUserId={}, requestId={}", 
                            request.getToUserId(), request.getRequestId(), e);
                    responseObserver.onNext(buildGrpcResponse(AnswerCode.ERROR));
                }
                responseObserver.onCompleted();
                return;
            }

            // 用户在线，直接推送
            ImProtoResponse response = ImProtoResponse.newBuilder()
                    .setType(MsgType.FRIEND_RESPONSE)  // 好友响应消息类型
                    .setPayload(com.google.protobuf.ByteString.copyFrom(request.toByteArray()))
                    .setCode(ProtoResponseCode.SUCCESS)
                    .build();

            boolean success = sendProtoToClient(targetChannel, response);
            
            if (success) {
                log.info("好友响应推送成功: toUserId={}, requestId={}", request.getToUserId(), request.getRequestId());
                responseObserver.onNext(buildGrpcResponse(AnswerCode.SUCCESS));
            } else {
                log.error("好友响应推送失败: toUserId={}, requestId={}", request.getToUserId(), request.getRequestId());
                responseObserver.onNext(buildGrpcResponse(AnswerCode.ERROR));
            }
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("gRPC推送好友响应异常:", e);
            com.xzll.grpc.WebBaseResponse errorResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(AnswerCode.ERROR.getCode())
                    .setMessage("处理失败: " + e.getMessage())
                    .setSuccess(false)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * 构建gRPC响应
     */
    private com.xzll.grpc.WebBaseResponse buildGrpcResponse(AnswerCode answerCode) {
        return com.xzll.grpc.WebBaseResponse.newBuilder()
                .setCode(answerCode.getCode())
                .setMessage(answerCode.getMessage())
                .setSuccess(answerCode == AnswerCode.SUCCESS)
                .build();
    }

} 