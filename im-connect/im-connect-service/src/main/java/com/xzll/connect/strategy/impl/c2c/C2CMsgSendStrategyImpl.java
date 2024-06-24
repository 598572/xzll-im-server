package com.xzll.connect.strategy.impl.c2c;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.response.C2CSendMsgVO;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.common.pojo.base.ImBaseRequest;


import com.xzll.connect.cluster.provider.C2CMsgProvider;
import com.xzll.connect.netty.channel.LocalChannelManager;

import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.connect.pojo.dto.ReceiveUserDataDTO;
import com.xzll.connect.pojo.dto.ServerInfoDTO;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.connect.service.TransferC2CMsgService;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.rpc.cluster.specifyaddress.Address;
import org.apache.dubbo.rpc.cluster.specifyaddress.UserSpecifiedAddressUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2022/1/14 15:20:10
 * @Description: 单聊消息
 */
@Slf4j
@Service
public class C2CMsgSendStrategyImpl extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {

    private static final String TAG = "[客户端发送单聊消息]_";

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    //需要此注解 否则 C2CMsgSendStrategyImpl 和 TransferC2CMsgService将循环依赖
    @Lazy
    @Resource
    private TransferC2CMsgService transferC2CMsgService;
    @Resource
    private C2CMsgProvider c2CMsgProvider;

    /**
     * 策略适配
     *
     * @param msgType
     * @return
     */
    @Override
    public boolean support(ImBaseRequest.MsgType msgType) {
        return ImBaseRequest.checkSupport(msgType, MsgTypeEnum.FirstLevelMsgType.CHAT_MSG.getCode(), MsgTypeEnum.SecondLevelMsgType.C2C.getCode());
    }

    /**
     * 根据不同类型适配不同的消息格式
     *
     * @param imBaseRequest
     * @return
     */
    public C2CSendMsgAO supportPojo(ImBaseRequest imBaseRequest) {
        C2CSendMsgAO packet = objectMapper.convertValue(imBaseRequest.getBody(), C2CSendMsgAO.class);
        //以服务器时间为准
        packet.setMsgCreateTime(System.currentTimeMillis());
        packet.setMsgType(imBaseRequest.getMsgType());
        return packet;
    }


    @Override
    public void exchange(ChannelHandlerContext ctx, ImBaseRequest imBaseRequest) {
        log.debug("客户端发送单聊消息_开始");

        C2CSendMsgAO packet = this.supportPojo(imBaseRequest);

        //1. 更新会话记录并保存消息记录（此处是消息表新增的唯一入口,将使用mq方式削峰解耦，避免大量写请求直接打到mysql）
        c2CMsgProvider.sendC2CMsg(packet);

        //2. 获取接收人登录，服务信息，根据状态进行处理
        ReceiveUserDataDTO receiveUserData = super.getReceiveUserDataTemplate(packet.getToUserId(), this.redisTemplate);

        String channelIdByUserId = receiveUserData.getChannelIdByUserId();
        Channel targetChannel = receiveUserData.getTargetChannel();
        String ipPortStr = receiveUserData.getRouteAddress();
        String userStatus = receiveUserData.getUserStatus();
        ServerInfoDTO serverInfoDTO = receiveUserData.getServerInfoDTO();
        log.info((TAG + "接收者id:{},在线状态:{},channelId:{},serverInfo:{}"), packet.getToUserId(), userStatus, channelIdByUserId, serverInfoDTO);

        //3. 根据接收人状态做对应的处理
        if (null != targetChannel && Objects.equals(ImConstant.UserStatus.ON_LINE.getValue().toString(), userStatus)) {
            // 直接发送
            log.info((TAG + "用户{}在线且在本台机器上,将直接发送"), packet.getToUserId());
            super.msgSendTemplate(TAG, targetChannel, JSONUtil.toJsonStr(buildC2CSendMsgVO(packet)));
        } else if (null == userStatus && null == targetChannel) {
            log.info((TAG + "用户{}不在线，将消息保存至离线表中"), packet.getToUserId());
            // 发送mq消息，记录离线消息并更新db中消息状态为离线
            c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
        } else if (Objects.isNull(targetChannel) && Objects.equals(ImConstant.UserStatus.ON_LINE.toString(), userStatus)
                && StringUtils.isNotBlank(ipPortStr)) {
            log.info((TAG + "用户{}在线但是不在该机器上,跳转到用户所在的服务器,服务器信息:{}"), packet.getToUserId(), ipPortStr);

            // 根据provider的ip,port创建Address实例并调用
            //dubbo 2.7.13 使用此方式指定 ip 调用
            //Address address = new Address(NettyAttrUtil.getIpStr(ipPortStr), NettyAttrUtil.getPortInt(ipPortStr));
            //RpcContext.getContext().setObjectAttachment("address", address);
            //dubbo 3.x 使用此方式指定 ip:port 调用 官方建议：[必须每次都设置，而且设置后必须马上发起调用]， 这里无需指定端口，指定ip就足够
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPortStr), 0, false));
            transferC2CMsgService.transferC2CMsg(imBaseRequest);
        }
        log.debug("客户端发送单聊消息_结束");
    }


    @Override
    public WebBaseResponse<String> receiveAndSendMsg(ImBaseRequest msg) {
        log.debug("目标服务器接收并转发消息_开始");
        C2CSendMsgAO packet = supportPojo(msg);
        Channel targetChannel = LocalChannelManager.getChannelByUserId(packet.getToUserId());
        String userStatus = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX, packet.getToUserId());
        //二次校验接收人在线状态
        if (StringUtils.isNotBlank(userStatus) && null != targetChannel) {
            log.info((TAG + "跳转后用户{}在线,将直接发送消息"), packet.getToUserId());
            super.msgSendTemplate(TAG, targetChannel, JSONUtil.toJsonStr(buildC2CSendMsgVO(packet)));
        } else {
            log.info((TAG + "跳转后用户{}不在线,将消息保存至离线表中"), packet.getToUserId());
            // 发送mq消息，记录离线消息并更新db中消息状态为离线
            c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));
        }
        log.debug("目标服务器接收并转发消息_结束");
        return WebBaseResponse.returnResultSuccess("跳转消息成功");
    }


    /**
     * 更新消息为离线
     *
     * @param packet
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
        build.setMsgType(packet.getMsgType());
        build.setMsgCreateTime(packet.getMsgCreateTime());
        return build;
    }

    private C2CSendMsgVO buildC2CSendMsgVO(C2CSendMsgAO packet) {
        C2CSendMsgVO c2CSendMsgVO = new C2CSendMsgVO();
        BeanUtil.copyProperties(packet,c2CSendMsgVO);
        return c2CSendMsgVO;
    }


}
