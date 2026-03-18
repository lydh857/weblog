package com.blog.system.mapper;

import com.blog.system.entity.LoginLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 登录日志 Mapper
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    @Select("""
            SELECT ip, COUNT(*) AS failedCount
            FROM t_login_log
            WHERE result = 'failed'
              AND create_time >= #{since}
              AND ip IS NOT NULL
              AND ip <> ''
            GROUP BY ip
            HAVING COUNT(*) >= #{threshold}
            ORDER BY failedCount DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectTopFailedIps(@Param("since") LocalDateTime since,
                                                 @Param("threshold") int threshold,
                                                 @Param("limit") int limit);
}
