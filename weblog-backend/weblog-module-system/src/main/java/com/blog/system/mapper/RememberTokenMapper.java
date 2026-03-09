package com.blog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.system.entity.RememberToken;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Remember Token Mapper
 */
@Mapper
public interface RememberTokenMapper extends BaseMapper<RememberToken> {

    /**
     * 根据 Token 查询
     */
    @Select("SELECT * FROM t_remember_token WHERE token = #{tokenHash} OR token = #{legacyToken} LIMIT 1")
    RememberToken selectByToken(@Param("tokenHash") String tokenHash, @Param("legacyToken") String legacyToken);

    /**
     * 使 Token 失效
     */
    @Update("UPDATE t_remember_token SET is_valid = 0 WHERE token = #{tokenHash} OR token = #{legacyToken}")
    int invalidateToken(@Param("tokenHash") String tokenHash, @Param("legacyToken") String legacyToken);

    /**
     * 清理过期 Token
     */
    @Delete("DELETE FROM t_remember_token WHERE expire_time < #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * 使用户的所有 Token 失效
     */
    @Update("UPDATE t_remember_token SET is_valid = 0 WHERE user_id = #{userId}")
    int invalidateAllUserTokens(@Param("userId") Long userId);
}
