package com.xzll.common.pojo.response;

import com.xzll.common.pojo.response.base.CommonMsgVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
public class ClientGetBatchMsgIdVO extends CommonMsgVO {

    /**
     * 一批消息id
     */
    private List<String> msgIds;

}
