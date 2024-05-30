package com.xzll.connect.pojo.dto;

import com.xzll.connect.pojo.dto.base.BaseMsgRequestDTO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
public class C2CMsgRequestDTO extends BaseMsgRequestDTO {

    private String fromUserId;
    private String toUserId;
    private Integer msgFormat;
    private String msgContent;

    private String firstUserName;

    private Integer firstUserType;

    private String secondUserName;

    private Integer secondUserType;


}
