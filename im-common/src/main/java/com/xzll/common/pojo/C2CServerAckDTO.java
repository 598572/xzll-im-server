package com.xzll.common.pojo;

import com.xzll.common.pojo.base.BaseMsgRequestDTO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
public class C2CServerAckDTO extends BaseMsgRequestDTO {

    /**
     * server ack 目标userId
     */
    private String toUserId;

}
