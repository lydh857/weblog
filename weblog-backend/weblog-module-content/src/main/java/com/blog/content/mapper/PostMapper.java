package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

/**
 * 文章 Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /** 分页查询已软删除的文章 */
    @Select("<script>" +
            "SELECT * FROM t_post WHERE is_deleted = 1" +
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

}
