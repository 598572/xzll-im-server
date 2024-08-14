package com.xzll.common.pojo.request.base;


import com.xzll.common.pojo.base.ImBaseRequest;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2022/1/18 18:09:06
 * @Description: 客户端发送消息-消息体基本属性抽取：所有【客户端】向《服务端》发的body 都继承于此 且后缀均为  AO ！
 */
@Getter
@Setter
public class CommonMsgAO implements Serializable {

    private static final long serialVersionUID = -1L;

    private String msgId;

    private String url;

    private Long msgCreateTime;

    private String chatId;


}
