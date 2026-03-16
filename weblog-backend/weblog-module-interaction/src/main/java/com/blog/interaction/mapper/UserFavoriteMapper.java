package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.UserFavorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户收藏 Mapper
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    @Insert("""
            INSERT INTO t_user_favorite (user_id, post_id, create_time, is_deleted)
            VALUES (#{userId}, #{postId}, NOW(), 0)
            ON DUPLICATE KEY UPDATE
              is_deleted = 0,
              create_time = NOW()
            """)
    int upsertActive(@Param("userId") Long userId, @Param("postId") Long postId);

    @Update("""
            UPDATE t_user_favorite
            SET is_deleted = 1
            WHERE user_id = #{userId}
              AND post_id = #{postId}
              AND is_deleted = 0
            """)
    int softDeleteByUnique(@Param("userId") Long userId, @Param("postId") Long postId);

    @Select("SELECT * FROM t_user_favorite WHERE user_id = #{userId} AND post_id = #{postId} LIMIT 1")
    UserFavorite selectIncludeDeleted(@Param("userId") Long userId, @Param("postId") Long postId);

    @Update("UPDATE t_user_favorite SET is_deleted = 0, create_time = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
