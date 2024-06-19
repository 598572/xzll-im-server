package com.xzll.connect.pojo.dto;

import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
public class WithdrawMsgAO extends CommonMsgAO {

    private String fromUserId;
    private String toUserId;

}
