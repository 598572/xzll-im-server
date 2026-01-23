package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.AiChatDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话记录Mapper
 *
 * @author xzll
 */
@Mapper
public interface AiChatMapper extends BaseMapper<AiChatDO> {
}
