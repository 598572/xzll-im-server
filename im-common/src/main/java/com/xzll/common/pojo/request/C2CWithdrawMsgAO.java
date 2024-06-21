package com.xzll.common.pojo.request;

import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2024/6/16 17:33:02
 * @Description:
 */
@Setter
@Getter
public class C2CWithdrawMsgAO extends CommonMsgAO {

    private String fromUserId;

    private String toUserId;

    private Integer withdrawFlag;

}
