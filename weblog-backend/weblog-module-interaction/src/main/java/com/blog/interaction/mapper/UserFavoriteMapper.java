package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.UserFavorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    @Select("SELECT id, user_id, post_id, create_time, is_deleted FROM t_user_favorite WHERE user_id = #{userId} AND post_id = #{postId} LIMIT 1")
    UserFavorite selectIncludeDeleted(@Param("userId") Long userId, @Param("postId") Long postId);

    @Update("UPDATE t_user_favorite SET is_deleted = 0, create_time = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    @Update("<script>" +
            "UPDATE t_user_favorite " +
            "SET is_deleted = 1 " +
            "WHERE user_id = #{userId} " +
            "AND is_deleted = 0 " +
            "AND post_id IN " +
            "<foreach collection='postIds' item='postId' open='(' separator=',' close=')'>#{postId}</foreach>" +
            "</script>")
    int softDeleteByUserAndPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
