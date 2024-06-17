package com.xzll.business.service;

import com.xzll.common.pojo.C2CMsgRequestDTO;

/**
 * @Author: hzz
 * @Date: 2024/6/16 15:58:34
 * @Description:
 */
public interface ImChatService {
    public boolean saveOrUpdateC2CChat(C2CMsgRequestDTO dto);
}
