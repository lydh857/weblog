package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.Carousel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 轮播配置 Mapper
 */
@Mapper
public interface CarouselMapper extends BaseMapper<Carousel> {

  /** 查询所有启用轮播图的图片 URL */
  @Select("SELECT image_url FROM t_carousel WHERE is_deleted = 0 AND is_enabled = 1 AND image_url IS NOT NULL AND image_url <> ''")
  java.util.List<String> selectEnabledImageUrls();

  /** 查询所有未删除轮播配置的图片 URL，用于媒体引用保护 */
  @Select("SELECT image_url FROM t_carousel WHERE is_deleted = 0 AND image_url IS NOT NULL AND image_url <> ''")
  java.util.List<String> selectConfiguredImageUrls();
}
