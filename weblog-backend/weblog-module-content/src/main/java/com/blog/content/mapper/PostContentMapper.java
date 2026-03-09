package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.PostContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章内容 Mapper
 */
@Mapper
public interface PostContentMapper extends BaseMapper<PostContent> {

    /** 查询指定文章 ID 列表的 Markdown 内容（仅投影 content 字段） */
    @Select("<script>" +
            "SELECT content FROM t_post_content WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    java.util.List<String> selectContentsByPostIds(@Param("ids") java.util.List<Long> ids);

    /** 查询指定文章 ID 列表的 id 和 content（用于引用来源检测） */
    @Select("<script>" +
            "SELECT id, content FROM t_post_content WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    java.util.List<java.util.Map<String, Object>> selectIdAndContentByPostIds(@Param("ids") java.util.List<Long> ids);
}
