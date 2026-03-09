package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.PostReadDaily;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章日阅读统计 Mapper
 */
@Mapper
public interface PostReadDailyMapper extends BaseMapper<PostReadDaily> {
}
