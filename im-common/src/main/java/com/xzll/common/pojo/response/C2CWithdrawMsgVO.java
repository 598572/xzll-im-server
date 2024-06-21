package com.xzll.common.pojo.response;

import com.xzll.common.pojo.response.base.CommonMsgVO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2024/6/16 17:33:02
 * @Description:
 */
@Setter
@Getter
public class C2CWithdrawMsgVO extends CommonMsgVO {

    private String fromUserId;

    private String toUserId;

    private Integer withdrawFlag;

}
