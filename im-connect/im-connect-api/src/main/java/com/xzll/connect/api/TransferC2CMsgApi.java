package com.xzll.connect.api;


import com.xzll.common.pojo.BaseResponse;
import com.xzll.common.pojo.MsgBaseRequest;

/**
 * @Author: hzz
 * @Date: 2024/5/30 15:56:57
 * @Description:
 */
public interface TransferC2CMsgApi {

    public BaseResponse transferC2CMsg(MsgBaseRequest msgBaseRequest);
}
