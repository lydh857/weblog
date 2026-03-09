package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.UserViewLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户阅读日志 Mapper
 */
@Mapper
public interface UserViewLogMapper extends BaseMapper<UserViewLog> {
}
