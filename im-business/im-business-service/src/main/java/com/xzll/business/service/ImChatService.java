package com.xzll.business.service;

import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.LastChatListAO;
import com.xzll.common.pojo.response.LastChatListVO;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/16 15:58:34
 * @Description:
 */
public interface ImChatService {

    /**
     * 保存/更新会话信息
     *
     * @param dto
     * @return
     */
    public boolean saveOrUpdateC2CChat(C2CSendMsgAO dto);

    /**
     * 查询最近会话列表
     *
     * @param ao
     * @return
     */
    public List<LastChatListVO> findLastChatList(LastChatListAO ao);
}
