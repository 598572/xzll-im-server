package com.xzll.connect.grpc;

import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.response.C2CWithdrawMsgVO;
import com.xzll.common.pojo.response.base.CommonMsgVO;
import com.xzll.connect.netty.channel.LocalChannelManager;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.connect.service.TransferC2CMsgService;
import io.grpc.stub.StreamObserver;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;

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

    @Override
    public void responseServerAck2Client(com.xzll.grpc.CommonMsgRequest request, 
                                       StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            Assert.isTrue(Objects.nonNull(request), "参数错误");
            Assert.isTrue(StringUtils.isNotBlank(request.getToUserId()), "发送服务端ack时缺少必填参数");
            
            // 构建响应服务端是否接收成功消息
            C2CServerReceivedMsgAckVO ackVo = buildServerAckVO(request);
            ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(request.getUrl(), ackVo);
            
            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
            
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
    public void responseClientAck2Client(com.xzll.grpc.CommonMsgRequest request, 
                                       StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            Assert.isTrue(Objects.nonNull(request), "参数错误");
            Assert.isTrue(StringUtils.isNotBlank(request.getToUserId()), "发送客户端ack时缺少必填参数");
            
            // 构建响应消息接收方客户端是否接收成功消息
            C2CClientReceivedMsgAckVO ackDTO = buildClientAckVO(request);
            ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(ackDTO.getUrl(), ackDTO);
            
            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
            
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
    public void sendWithdrawMsg2Client(com.xzll.grpc.CommonMsgRequest request, 
                                     StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            Assert.isTrue(Objects.nonNull(request), "参数错误");
            
            // 构建响应消息接收方客户端是否接收成功消息
            C2CWithdrawMsgVO withdrawMsgVo = buildWithdrawMsgVO(request);
            ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(withdrawMsgVo.getUrl(), withdrawMsgVo);
            
            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            boolean result = this.sendMsg2Client(targetChannel, imBaseResponse);
            
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
    public void transferC2CMsg(com.xzll.grpc.ImBaseRequest request, 
                              StreamObserver<com.xzll.grpc.WebBaseResponse> responseObserver) {
        try {
            // 这里需要调用原来的消息转发逻辑
            // 将gRPC请求转换为ImBaseRequest
            ImBaseRequest imBaseRequest = convertGrpcToImBaseRequest(request);
            
            // 调用原有的转发服务
            WebBaseResponse<String> result = transferC2CMsgService.transferC2CMsg(imBaseRequest);
            
            com.xzll.grpc.WebBaseResponse grpcResponse = com.xzll.grpc.WebBaseResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMsg())
                    .setData(result.getData() != null ? result.getData().toString() : "")
                    .setSuccess(result.getCode() == AnswerCode.SUCCESS.getCode())
                    .build();
            
            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
            
            log.info("gRPC转发C2C消息成功，响应: {}", result.getMsg());
            
        } catch (Exception e) {
            log.error("gRPC转发C2C消息异常:", e);
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
    public void batchSendToUsers(com.xzll.grpc.BatchSendRequest request, 
                                StreamObserver<com.xzll.grpc.BatchSendResponse> responseObserver) {
        try {
            // 实现批量发送逻辑
            com.xzll.grpc.BatchSendResponse.Builder responseBuilder = com.xzll.grpc.BatchSendResponse.newBuilder();
            
            int totalCount = request.getUserIdsCount();
            int successCount = 0;
            int failureCount = 0;
            
            for (String userId : request.getUserIdsList()) {
                try {
                    // 构建单个消息请求
                    com.xzll.grpc.CommonMsgRequest singleRequest = com.xzll.grpc.CommonMsgRequest.newBuilder()
                            .setToUserId(userId)
                            .setUrl(request.getMessage().getUrl())
                            .setMsgId(request.getMessage().getMsgId())
                            .setChatId(request.getMessage().getChatId())
                            .setFromUserId(request.getMessage().getFromUserId())
                            .setMsgContent(request.getMessage().getMsgContent())
                            .setMsgFormat(request.getMessage().getMsgFormat())
                            .setMsgCreateTime(request.getMessage().getMsgCreateTime())
                            .setMsgStatus(request.getMessage().getMsgStatus())
                            .build();
                    
                    // 根据消息类型调用相应的方法
                    boolean success = false;
                    switch (request.getMessageType()) {
                        case "SERVER_ACK":
                            success = sendServerAckInternal(singleRequest);
                            break;
                        case "CLIENT_ACK":
                            success = sendClientAckInternal(singleRequest);
                            break;
                        case "WITHDRAW":
                            success = sendWithdrawMsgInternal(singleRequest);
                            break;
                        default:
                            log.warn("未知的消息类型: {}", request.getMessageType());
                            success = false;
                    }
                    
                    // 构建用户结果
                    com.xzll.grpc.UserSendResult userResult = com.xzll.grpc.UserSendResult.newBuilder()
                            .setUserId(userId)
                            .setSuccess(success)
                            .setMessage(success ? "发送成功" : "发送失败")
                            .setError(success ? "" : "处理异常")
                            .build();
                    
                    responseBuilder.addResults(userResult);
                    
                    if (success) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                    
                } catch (Exception e) {
                    log.error("批量发送消息给用户 {} 失败: {}", userId, e.getMessage());
                    
                    com.xzll.grpc.UserSendResult userResult = com.xzll.grpc.UserSendResult.newBuilder()
                            .setUserId(userId)
                            .setSuccess(false)
                            .setMessage("发送失败")
                            .setError(e.getMessage())
                            .build();
                    
                    responseBuilder.addResults(userResult);
                    failureCount++;
                }
            }
            
            // 构建最终响应
            com.xzll.grpc.BatchSendResponse response = responseBuilder
                    .setTotalCount(totalCount)
                    .setSuccessCount(successCount)
                    .setFailureCount(failureCount)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.info("gRPC批量发送消息完成，总数: {}, 成功: {}, 失败: {}", totalCount, successCount, failureCount);
            
        } catch (Exception e) {
            log.error("gRPC批量发送消息异常:", e);
            // 返回错误响应
            com.xzll.grpc.BatchSendResponse errorResponse = com.xzll.grpc.BatchSendResponse.newBuilder()
                    .setTotalCount(0)
                    .setSuccessCount(0)
                    .setFailureCount(0)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * 发送消息到客户端
     */
    private boolean sendMsg2Client(Channel channel, ImBaseResponse packet) {
        try {
            if (Objects.nonNull(channel)) {
                channel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(packet)));
                return true;
            }
            log.error("服务端发送ack_传入的channel为空，不发送!");
        } catch (Exception e) {
            log.error("服务端发送ack_异常:", e);
        }
        return false;
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

    /**
     * 构建服务端ack VO
     */
    private C2CServerReceivedMsgAckVO buildServerAckVO(com.xzll.grpc.CommonMsgRequest request) {
        C2CServerReceivedMsgAckVO ackVo = new C2CServerReceivedMsgAckVO();
        ackVo.setAckTextDesc(request.getAckTextDesc());
        ackVo.setMsgReceivedStatus(request.getMsgReceivedStatus());
        ackVo.setReceiveTime(request.getReceiveTime());
        ackVo.setChatId(request.getChatId());
        ackVo.setToUserId(request.getToUserId());
        ackVo.setUrl(request.getUrl());
        ackVo.setMsgId(request.getMsgId());
        return ackVo;
    }

    /**
     * 构建客户端ack VO
     */
    private C2CClientReceivedMsgAckVO buildClientAckVO(com.xzll.grpc.CommonMsgRequest request) {
        C2CClientReceivedMsgAckVO ackDTO = new C2CClientReceivedMsgAckVO();
        ackDTO.setAckTextDesc(request.getAckTextDesc());
        ackDTO.setMsgReceivedStatus(request.getMsgReceivedStatus());
        ackDTO.setReceiveTime(request.getReceiveTime());
        ackDTO.setChatId(request.getChatId());
        ackDTO.setToUserId(request.getToUserId());
        ackDTO.setUrl(request.getUrl());
        ackDTO.setMsgId(request.getMsgId());
        return ackDTO;
    }

    /**
     * 构建撤回消息 VO
     */
    private C2CWithdrawMsgVO buildWithdrawMsgVO(com.xzll.grpc.CommonMsgRequest request) {
        C2CWithdrawMsgVO withdrawMsgVo = new C2CWithdrawMsgVO();
        withdrawMsgVo.setToUserId(request.getToUserId());
        withdrawMsgVo.setUrl(request.getUrl());
        withdrawMsgVo.setMsgId(request.getMsgId());
        // chatId 不在 CommonMsgVO 中，跳过设置
        return withdrawMsgVo;
    }

    /**
     * 将gRPC请求转换为ImBaseRequest
     */
    private ImBaseRequest convertGrpcToImBaseRequest(com.xzll.grpc.ImBaseRequest grpcRequest) {
        ImBaseRequest imBaseRequest = new ImBaseRequest();
        imBaseRequest.setUrl(grpcRequest.getUrl());
        imBaseRequest.setBody(grpcRequest.getBody().toByteArray());
        // 设置headers（如果需要）
        return imBaseRequest;
    }

    /**
     * 内部发送服务端ACK
     */
    private boolean sendServerAckInternal(com.xzll.grpc.CommonMsgRequest request) {
        try {
            C2CServerReceivedMsgAckVO ackVo = buildServerAckVO(request);
            ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(request.getUrl(), ackVo);
            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            return sendMsg2Client(targetChannel, imBaseResponse);
        } catch (Exception e) {
            log.error("内部发送服务端ACK失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 内部发送客户端ACK
     */
    private boolean sendClientAckInternal(com.xzll.grpc.CommonMsgRequest request) {
        try {
            C2CClientReceivedMsgAckVO ackDTO = buildClientAckVO(request);
            ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(ackDTO.getUrl(), ackDTO);
            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            return sendMsg2Client(targetChannel, imBaseResponse);
        } catch (Exception e) {
            log.error("内部发送客户端ACK失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 内部发送撤回消息
     */
    private boolean sendWithdrawMsgInternal(com.xzll.grpc.CommonMsgRequest request) {
        try {
            C2CWithdrawMsgVO withdrawMsgVo = buildWithdrawMsgVO(request);
            ImBaseResponse imBaseResponse = ImBaseResponse.buildPushToClientData(withdrawMsgVo.getUrl(), withdrawMsgVo);
            Channel targetChannel = LocalChannelManager.getChannelByUserId(request.getToUserId());
            return sendMsg2Client(targetChannel, imBaseResponse);
        } catch (Exception e) {
            log.error("内部发送撤回消息失败: {}", e.getMessage());
            return false;
        }
    }
} 