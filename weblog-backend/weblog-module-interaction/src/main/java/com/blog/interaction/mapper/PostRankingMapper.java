package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.PostRanking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PostRankingMapper extends BaseMapper<PostRanking> {

    /**
     * 查询排行榜（关联文章信息）
     */
    @Select("""
        <script>
        SELECT r.rank_num, r.score, r.post_id,
               p.title, p.slug, p.cover_image, p.view_count, p.like_count, p.collect_count, p.comment_count,
               c.name AS category_name,
               sc.name AS sub_category_name,
               GROUP_CONCAT(DISTINCT t.name ORDER BY t.id SEPARATOR '、') AS tag_names
        FROM t_post_ranking r
        INNER JOIN t_post p ON r.post_id = p.id AND p.is_deleted = 0 AND p.status = 'published' AND (p.topic_only = 0 OR p.topic_only IS NULL)
        LEFT JOIN t_category c ON p.category_id = c.id
        LEFT JOIN t_category sc ON p.sub_category_id = sc.id
        LEFT JOIN t_post_tag pt ON pt.post_id = r.post_id
        LEFT JOIN t_tag t ON pt.tag_id = t.id
        WHERE r.rank_type = #{rankType}
          AND r.category_id IS NULL
          <if test="statDate != null">AND r.stat_date = #{statDate}</if>
          <if test="statDate == null">AND r.stat_date IS NULL</if>
          <if test="categoryId != null">AND p.category_id = #{categoryId}</if>
        GROUP BY r.rank_num, r.score, r.post_id,
                 p.title, p.slug, p.cover_image, p.view_count, p.like_count, p.collect_count, p.comment_count,
                 c.name, sc.name
        ORDER BY r.rank_num ASC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<Map<String, Object>> selectRankingWithPost(
            @Param("rankType") int rankType,
            @Param("categoryId") Long categoryId,
            @Param("statDate") LocalDate statDate,
            @Param("limit") int limit,
            @Param("offset") int offset);
}
