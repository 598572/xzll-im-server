package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.AdminDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员Mapper
 *
 * @author xzll
 */
@Mapper
public interface AdminMapper extends BaseMapper<AdminDO> {
}
