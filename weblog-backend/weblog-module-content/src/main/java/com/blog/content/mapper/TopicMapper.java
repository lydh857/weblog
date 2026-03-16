package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.Topic;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 专题 Mapper
 */
@Mapper
public interface TopicMapper extends BaseMapper<Topic> {

    /** 查询所有已发布专题封面 URL */
    @Select("SELECT cover FROM t_topic WHERE is_deleted = 0 AND is_publish = 1 AND cover IS NOT NULL AND cover <> ''")
    java.util.List<String> selectPublishedCoverUrls();

    /** 查询所有已发布专题 id、title、cover（用于引用来源检测） */
    @Select("SELECT id, title, cover FROM t_topic WHERE is_deleted = 0 AND is_publish = 1")
    java.util.List<java.util.Map<String, Object>> selectPublishedTopicSummaries();

    /**
     * 分页查询专题列表，通过 LEFT JOIN 统计每个专题关联的文章数
     */
    @Select("<script>" +
            "SELECT t.*, IFNULL(c.article_count, 0) AS article_count " +
            "FROM t_topic t " +
            "LEFT JOIN (" +
            "  SELECT topic_id, COUNT(*) AS article_count " +
            "  FROM t_topic_catalog " +
            "  WHERE is_deleted = 0 AND article_id IS NOT NULL " +
            "  GROUP BY topic_id" +
            ") c ON t.id = c.topic_id " +
            "WHERE t.is_deleted = 0" +
            "<if test='keyword != null and keyword != \"\"'> AND t.title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "<if test='isPublish != null'> AND t.is_publish = #{isPublish}</if>" +
            "<if test='isTop != null'> AND t.is_top = #{isTop}</if>" +
            " ORDER BY t.weight DESC, t.create_time DESC" +
            "</script>")
    IPage<Map<String, Object>> selectPageWithArticleCount(
            Page<?> page,
            @Param("keyword") String keyword,
            @Param("isPublish") Boolean isPublish,
            @Param("isTop") Boolean isTop);

    /** 分页查询已软删除的专题（回收站） */
    @Select("<script>" +
            "SELECT * FROM t_topic WHERE is_deleted = 1" +
            "<if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            " ORDER BY update_time DESC" +
            "</script>")
    IPage<Topic> selectDeletedPage(Page<Topic> page, @Param("keyword") String keyword);

    /** 恢复软删除的专题（取消发布状态） */
    @Update("UPDATE t_topic SET is_deleted = 0, is_publish = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    /** 物理删除专题 */
    @Delete("DELETE FROM t_topic WHERE id = #{id} AND is_deleted = 1")
    int permanentDeleteById(@Param("id") Long id);

    /** 查询所有已删除专题的 ID */
    @Select("SELECT id FROM t_topic WHERE is_deleted = 1")
    List<Long> selectDeletedIds();
}
