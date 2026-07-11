package com.blog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.system.entity.BlacklistEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlacklistMapper extends BaseMapper<BlacklistEntry> {
}
