package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.dto.SearchHitMeta;
import com.blog.content.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 文章 Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /** 批量设置置顶状态（带作者权限校验） */
    @Update("<script>" +
            "UPDATE t_post SET is_top = #{isTop} WHERE is_deleted = 0 AND author_id = #{operatorId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchSetTop(@Param("ids") List<Long> ids, @Param("isTop") boolean isTop, @Param("operatorId") Long operatorId);

    /** 批量设置禁用状态（带作者权限校验） */
    @Update("<script>" +
            "UPDATE t_post SET is_disabled = #{isDisabled} WHERE is_deleted = 0 AND author_id = #{operatorId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchSetDisabled(@Param("ids") List<Long> ids, @Param("isDisabled") boolean isDisabled, @Param("operatorId") Long operatorId);

    /** 批量发布草稿（带作者权限校验，非管理员） */
    @Update("<script>" +
            "UPDATE t_post SET status = 'published', is_published = 1, publish_type = 'immediate', scheduled_time = NULL " +
            "WHERE is_deleted = 0 AND author_id = #{operatorId} AND status IN ('draft','scheduled') AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchPublishByAuthor(@Param("ids") List<Long> ids, @Param("operatorId") Long operatorId);

    /** 批量发布草稿（管理员，不限作者） */
    @Update("<script>" +
            "UPDATE t_post SET status = 'published', is_published = 1, publish_type = 'immediate', scheduled_time = NULL " +
            "WHERE is_deleted = 0 AND status IN ('draft','scheduled') AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchPublishByAdmin(@Param("ids") List<Long> ids);

    /** 批量恢复软删除文章 */
    @Update("<script>" +
            "UPDATE t_post SET is_deleted = 0, status = 'draft' WHERE is_deleted = 1 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchRestore(@Param("ids") List<Long> ids);

    /** 批量物理删除文章 */
    @Delete("<script>" +
            "DELETE FROM t_post WHERE is_deleted = 1 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchPermanentDelete(@Param("ids") List<Long> ids);

    /** 分页查询已软删除的文章 */
    @Select("<script>" +
            "SELECT id, title, slug, summary, cover_image, category_id, sub_category_id, author_id, " +
            "status, publish_type, scheduled_time, is_published, view_count, like_count, " +
            "collect_count, comment_count, seo_title, seo_description, seo_keywords, " +
            "preview_theme, code_theme, is_top, topic_only, is_disabled, is_deleted, " +
            "create_time, update_time " +
            "FROM t_post WHERE is_deleted = 1" +
            "<if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            " ORDER BY update_time DESC" +
            "</script>")
    IPage<Post> selectDeletedPage(Page<Post> page, @Param("keyword") String keyword);

    /** 恢复软删除的文章（设置 is_deleted=0, status=draft） */
    @Update("UPDATE t_post SET is_deleted = 0, status = 'draft' WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    /** 物理删除文章 */
    @Delete("DELETE FROM t_post WHERE id = #{id} AND is_deleted = 1")
    int permanentDeleteById(@Param("id") Long id);

    /** 统计指定 slug 的记录数（包含已软删除的，绕过 @TableLogic） */
    @Select("SELECT COUNT(*) FROM t_post WHERE slug = #{slug}")
    int countBySlugIncludeDeleted(@Param("slug") String slug);

    /** 文章是否存在（未软删除） */
    @Select("SELECT COUNT(1) FROM t_post WHERE id = #{id} AND is_deleted = 0")
    int existsById(@Param("id") Long id);

    /** 文章是否可在前台访问（未软删除、已发布、未禁用） */
    @Select("SELECT COUNT(1) FROM t_post WHERE id = #{id} AND is_deleted = 0 AND status = 'published' AND (is_disabled = 0 OR is_disabled IS NULL)")
    int existsReadableById(@Param("id") Long id);

    /** 批量查询存在的文章ID（未软删除） */
    @Select("<script>" +
            "SELECT id FROM t_post WHERE is_deleted = 0 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Long> selectExistingIds(@Param("ids") List<Long> ids);

    /** 统计已删除文章数 */
    @Select("SELECT COUNT(*) FROM t_post WHERE is_deleted = 1")
    int countDeleted();

    /** 查询所有已删除文章的ID */
    @Select("SELECT id FROM t_post WHERE is_deleted = 1")
    java.util.List<Long> selectDeletedIds();


    /** 查询所有未删除文章的封面图 URL（仅投影 cover_image） */
    @Select("SELECT cover_image FROM t_post WHERE is_deleted = 0 AND cover_image IS NOT NULL")
    java.util.List<String> selectAllCoverImages();

    /** 查询所有未删除文章的 ID */
    @Select("SELECT id FROM t_post WHERE is_deleted = 0")
    java.util.List<Long> selectActiveIds();

    /** 查询所有未删除文章的 id、title、cover_image（用于引用来源检测） */
    @Select("SELECT id, title, cover_image FROM t_post WHERE is_deleted = 0")
    java.util.List<java.util.Map<String, Object>> selectActivePostSummaries();

    @Select("<script>" +
            "SELECT p.id AS id, " +
            "p.view_count AS viewCount, " +
            "p.like_count AS likeCount, " +
            "p.collect_count AS collectCount, " +
            "p.comment_count AS commentCount, " +
            "p.create_time AS createTime, " +
            "c.name AS categoryName, " +
            "sc.name AS subCategoryName, " +
            "u.nickname AS authorNickname " +
            "FROM t_post p " +
            "LEFT JOIN t_category c ON c.id = p.category_id " +
            "LEFT JOIN t_category sc ON sc.id = p.sub_category_id " +
            "LEFT JOIN t_user u ON u.id = p.author_id AND u.is_deleted = 0 " +
            "WHERE p.is_deleted = 0 " +
            "AND p.status = 'published' " +
            "AND (p.is_disabled = 0 OR p.is_disabled IS NULL) " +
            "AND (p.topic_only = 0 OR p.topic_only IS NULL) " +
            "AND p.id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<SearchHitMeta> selectSearchHitMetaByIds(@Param("ids") List<Long> ids);

}
