package com.xzll.connect.pojo.dto;

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
public class SystemMsgRequestDTO extends BaseMsgRequestDTO {

    private String fromId;
    private String toId;
    /**
     * 业务类型，值待定
     */
    private Integer businessType;

    private String msgContent;



}
