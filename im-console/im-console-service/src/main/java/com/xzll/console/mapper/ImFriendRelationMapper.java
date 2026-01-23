package com.xzll.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.console.entity.ImFriendRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友关系Mapper
 */
@Mapper
public interface ImFriendRelationMapper extends BaseMapper<ImFriendRelationDO> {
    
    /**
     * 查询用户的好友列表
     */
    @Select("SELECT * FROM im_friend_relation WHERE user_id = #{userId} AND del_flag = 0")
    List<ImFriendRelationDO> selectFriendsByUserId(@Param("userId") String userId);
    
    /**
     * 统计用户好友数量
     */
    @Select("SELECT COUNT(*) FROM im_friend_relation WHERE user_id = #{userId} AND del_flag = 0")
    Long countFriendsByUserId(@Param("userId") String userId);
    
    /**
     * 统计总好友关系数（不重复）
     */
    @Select("SELECT COUNT(DISTINCT LEAST(user_id, friend_id), GREATEST(user_id, friend_id)) FROM im_friend_relation WHERE del_flag = 0")
    Long countTotalRelations();
}
