package com.xzll.common.rocketmq;

import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.TraceIdUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

/**
 * @Author: hzz
 * @Date: 2023/2/28 18:02:33
 * @Description:
 */
public class RocketMqSendMessageHook implements SendMessageHook {


	@Override
	public String hookName() {
		return "RocketMqSendMessageHook";
	}

	@Override
	public void sendMessageBefore(SendMessageContext sendMessageContext) {
		String traceId = TraceIdUtil.getTraceIdByLocal();
		if(StringUtils.isNotBlank(traceId)){
			sendMessageContext.getMessage().putUserProperty(ImConstant.TraceConstant.TRACE_ID, traceId);
		}
	}


	@Override
	public void sendMessageAfter(SendMessageContext sendMessageContext) {
	}
}
