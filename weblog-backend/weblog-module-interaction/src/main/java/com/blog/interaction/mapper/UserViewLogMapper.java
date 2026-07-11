package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.UserViewLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户阅读日志 Mapper
 */
@Mapper
public interface UserViewLogMapper extends BaseMapper<UserViewLog> {

    @Insert("""
            INSERT INTO t_user_view_log (device_hash, user_id, post_id, view_date, view_count, ip_address)
            VALUES (#{deviceHash}, #{userId}, #{postId}, #{viewDate}, 1, #{ipAddress})
            ON DUPLICATE KEY UPDATE
              view_count = view_count + 1,
              user_id = COALESCE(#{userId}, user_id),
              post_id = #{postId},
              ip_address = #{ipAddress}
            """)
    int upsertDailyView(@Param("deviceHash") String deviceHash,
                        @Param("userId") Long userId,
                        @Param("postId") Long postId,
                        @Param("viewDate") java.time.LocalDate viewDate,
                        @Param("ipAddress") String ipAddress);
}
