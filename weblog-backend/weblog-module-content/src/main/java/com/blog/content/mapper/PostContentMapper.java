package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.PostContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 分页查询未删除文章的内容（JOIN t_post 过滤软删除），按 id 升序分页。
     * 避免一次性加载全量文章内容到内存。
     *
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 内容列表，每项包含 id 和 content
     */
    @Select("SELECT pc.id, pc.content FROM t_post_content pc " +
            "INNER JOIN t_post p ON p.id = pc.id AND p.is_deleted = 0 " +
            "WHERE pc.id > #{offset} ORDER BY pc.id ASC LIMIT #{limit}")
    List<java.util.Map<String, Object>> selectActiveContentPageById(@Param("offset") long offset, @Param("limit") int limit);
}
