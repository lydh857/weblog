package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
