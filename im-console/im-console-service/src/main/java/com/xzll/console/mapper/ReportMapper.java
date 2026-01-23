package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.ReportDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报记录Mapper
 *
 * @author xzll
 */
@Mapper
public interface ReportMapper extends BaseMapper<ReportDO> {
}
