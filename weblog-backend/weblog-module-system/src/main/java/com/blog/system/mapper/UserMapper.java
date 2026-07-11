package com.blog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 查询有效用户头像 URL */
    @Select("SELECT avatar FROM t_user WHERE is_deleted = 0 AND avatar IS NOT NULL AND avatar <> ''")
    java.util.List<String> selectActiveAvatarUrls();

    /** 查询有效用户头像引用详情 */
    @Select("SELECT id, nickname, email, avatar FROM t_user WHERE is_deleted = 0 AND avatar IS NOT NULL AND avatar <> ''")
    java.util.List<java.util.Map<String, Object>> selectActiveAvatarSummaries();

    @Update("""
            UPDATE t_user
            SET failed_login_attempts = COALESCE(failed_login_attempts, 0) + 1,
                status = CASE
                    WHEN COALESCE(failed_login_attempts, 0) + 1 >= #{maxAttempts} THEN 'locked'
                    ELSE status
                END,
                lock_until = CASE
                    WHEN COALESCE(failed_login_attempts, 0) + 1 >= #{maxAttempts} THEN #{lockUntil}
                    ELSE lock_until
                END
            WHERE id = #{userId}
            """)
    int incrementFailedAttemptsAndLock(@Param("userId") Long userId,
                                       @Param("maxAttempts") int maxAttempts,
                                       @Param("lockUntil") LocalDateTime lockUntil);
}
