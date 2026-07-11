package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.UserLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户点赞 Mapper
 */
@Mapper
public interface UserLikeMapper extends BaseMapper<UserLike> {

    @Insert("""
            INSERT INTO t_user_like (user_id, target_type, target_id, create_time, is_deleted)
            VALUES (#{userId}, #{targetType}, #{targetId}, NOW(), 0)
            ON DUPLICATE KEY UPDATE
              is_deleted = 0,
              create_time = NOW()
            """)
    int upsertActive(@Param("userId") Long userId,
                     @Param("targetType") String targetType,
                     @Param("targetId") Long targetId);

    @Update("""
            UPDATE t_user_like
            SET is_deleted = 1
            WHERE user_id = #{userId}
              AND target_type = #{targetType}
              AND target_id = #{targetId}
              AND is_deleted = 0
            """)
    int softDeleteByUnique(@Param("userId") Long userId,
                           @Param("targetType") String targetType,
                           @Param("targetId") Long targetId);

    /**
     * 查询记录（包含已软删除的），绕过 @TableLogic
     */
    @Select("SELECT id, user_id, target_type, target_id, create_time, is_deleted FROM t_user_like WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId} LIMIT 1")
    UserLike selectIncludeDeleted(@Param("userId") Long userId,
                                  @Param("targetType") String targetType,
                                  @Param("targetId") Long targetId);

    /**
     * 恢复软删除记录
     */
    @Update("UPDATE t_user_like SET is_deleted = 0, create_time = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
