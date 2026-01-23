package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.ImChatDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @Author: hzz
 * @Date: 2026/01/23
 * @Description: 会话Mapper
 */
@Mapper
public interface ImChatMapper extends BaseMapper<ImChatDO> {
    
    /**
     * 统计总会话数
     */
    @Select("SELECT COUNT(*) FROM im_chat")
    Long countTotal();
    
    /**
     * 统计单聊会话数
     */
    @Select("SELECT COUNT(*) FROM im_chat WHERE chat_type = 1")
    Long countSingleChat();
    
    /**
     * 统计群聊会话数
     */
    @Select("SELECT COUNT(*) FROM im_chat WHERE chat_type = 2")
    Long countGroupChat();
}
