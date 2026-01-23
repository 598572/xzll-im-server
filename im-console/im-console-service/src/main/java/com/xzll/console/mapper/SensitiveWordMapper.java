package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.SensitiveWordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 敏感词Mapper
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWordDO> {
    
    /**
     * 查询所有启用的敏感词
     */
    @Select("SELECT word FROM im_sensitive_word WHERE status = 1")
    List<String> selectAllEnabledWords();
    
    /**
     * 按类型查询敏感词
     */
    @Select("SELECT * FROM im_sensitive_word WHERE word_type = #{wordType} AND status = 1")
    List<SensitiveWordDO> selectByType(Integer wordType);
}
