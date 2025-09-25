package com.xzll.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.auth.entity.ImResourceRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 资源权限配置Mapper
 */
@Mapper
public interface ImResourceRoleMapper extends BaseMapper<ImResourceRole> {

    /**
     * 查询所有启用的资源权限配置
     *
     * @return 资源权限配置列表
     */
    List<ImResourceRole> selectEnabledResources();
}
