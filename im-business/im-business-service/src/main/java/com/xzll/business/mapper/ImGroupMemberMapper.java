package com.xzll.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzll.business.entity.mysql.ImGroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群成员关系Mapper
 */
@Mapper
public interface ImGroupMemberMapper extends BaseMapper<ImGroupMember> {

    /**
     * 查询用户加入的所有群ID（只查询正常状态的群）
     *
     * @param userId 用户ID
     * @return 群ID列表
     */
    @Select("SELECT DISTINCT gm.group_id " +
            "FROM im_group_member gm " +
            "INNER JOIN im_group g ON gm.group_id = g.group_id " +
            "WHERE gm.user_id = #{userId} " +
            "AND gm.status = 1 " +
            "AND g.status = 1")
    List<String> selectGroupIdsByUserId(@Param("userId") String userId);

    /**
     * 查询用户加入的所有群（包含群信息）
     *
     * @param userId 用户ID
     * @return 群成员信息列表
     */
    @Select("SELECT gm.group_id, gm.member_role, gm.nickname, " +
            "g.group_name, g.group_avatar, g.current_count " +
            "FROM im_group_member gm " +
            "LEFT JOIN im_group g ON gm.group_id = g.group_id " +
            "WHERE gm.user_id = #{userId} " +
            "AND gm.status = 1 " +
            "AND g.status = 1")
    List<ImGroupMember> selectGroupsWithInfoByUserId(@Param("userId") String userId);

    /**
     * 查询群组的所有成员（只查询正常状态的成员）
     *
     * @param groupId 群组ID
     * @return 群成员列表
     */
    @Select("SELECT gm.* " +
            "FROM im_group_member gm " +
            "WHERE gm.group_id = #{groupId} " +
            "AND gm.status = 1 " +
            "ORDER BY gm.member_role ASC, gm.create_time ASC")
    List<ImGroupMember> selectMembersByGroupId(@Param("groupId") String groupId);

    /**
     * 批量插入群成员（使用MyBatis foreach，性能优化）
     *
     * @param memberList 成员列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("list") List<ImGroupMember> memberList);
}
