package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.OperationLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志Mapper
 *
 * @author xzll
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogDO> {
}
