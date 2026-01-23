package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.ImUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 用户管理Mapper
 */
@Mapper
public interface ImUserMapper extends BaseMapper<ImUserDO> {
    
    /**
     * 统计今日注册用户数
     */
    @Select("SELECT COUNT(*) FROM im_user WHERE DATE(register_time) = CURDATE()")
    Long countTodayRegistered();
    
    /**
     * 统计总用户数
     */
    @Select("SELECT COUNT(*) FROM im_user")
    Long countTotal();
    
    /**
     * 按终端类型统计用户
     */
    @Select("SELECT register_terminal_type, COUNT(*) as count FROM im_user GROUP BY register_terminal_type")
    List<Object[]> countByTerminalType();
    
    /**
     * 模糊搜索用户
     */
    @Select("<script>" +
            "SELECT * FROM im_user WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (user_id LIKE CONCAT('%',#{keyword},'%') " +
            "OR user_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR user_full_name LIKE CONCAT('%',#{keyword},'%') " +
            "OR phone LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    List<ImUserDO> searchUsers(@Param("keyword") String keyword);
}
