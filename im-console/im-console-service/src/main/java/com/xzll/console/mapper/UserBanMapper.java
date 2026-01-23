package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.UserBanDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户封禁Mapper
 *
 * @author xzll
 */
@Mapper
public interface UserBanMapper extends BaseMapper<UserBanDO> {
}
