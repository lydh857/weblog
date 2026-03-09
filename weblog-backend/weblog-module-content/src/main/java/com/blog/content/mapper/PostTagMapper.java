package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.PostTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.blog.content.vo.TagCloudVO;

import java.util.List;

/**
 * 文章标签关联 Mapper
 */
@Mapper
public interface PostTagMapper extends BaseMapper<PostTag> {

    /**
     * 标签云查询：单条 SQL 完成 JOIN + 分类筛选 + 文章计数
     * 仅返回有已发布文章关联的标签
     */
    @Select({"<script>",
        "SELECT t.id, t.name, t.slug, t.color, COUNT(DISTINCT pt.post_id) AS postCount",
        "FROM t_tag t",
        "INNER JOIN t_post_tag pt ON t.id = pt.tag_id",
        "INNER JOIN t_post p ON pt.post_id = p.id",
        "WHERE p.status = 'published' AND p.is_deleted = 0",
        "<if test='subCategoryId != null'>",
        "  AND p.sub_category_id = #{subCategoryId}",
        "</if>",
        "<if test='subCategoryId == null and categoryId != null'>",
        "  AND (p.category_id = #{categoryId} OR p.sub_category_id IN (",
        "    SELECT c.id FROM t_category c WHERE c.parent_id = #{categoryId}",
        "  ))",
        "</if>",
        "GROUP BY t.id, t.name, t.slug, t.color",
        "ORDER BY postCount DESC",
        "</script>"})
    List<TagCloudVO> selectTagCloudWithCount(
            @Param("categoryId") Long categoryId,
            @Param("subCategoryId") Long subCategoryId);
}
