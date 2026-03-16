package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.FriendLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 友链 Mapper
 */
@Mapper
public interface FriendLinkMapper extends BaseMapper<FriendLink> {

  /** 查询有效友链 Logo URL */
  @Select("SELECT logo FROM t_friend_link WHERE status IN ('active','pending') AND logo IS NOT NULL AND logo <> ''")
  java.util.List<String> selectEffectiveLogoUrls();
}
