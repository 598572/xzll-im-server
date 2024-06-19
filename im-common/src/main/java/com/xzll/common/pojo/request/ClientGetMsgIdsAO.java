package com.xzll.common.pojo.request;


import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
@Accessors(chain = true)
public class ClientGetMsgIdsAO extends CommonMsgAO {

    private String fromUserId;

}
