package com.xzll.common.pojo.request;

import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description: 最近会话列表 ao
 */
@Setter
@Getter
public class LastChatListAO extends CommonMsgAO {


    private Integer currentPage;

    private Integer pageSize;


    /**
     * 当前用户id 可不传，服务端根据channel也能获取userId
     */
    private String userId;


}
