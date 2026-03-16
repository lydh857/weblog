package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.content.entity.Advertisement;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdvertisementMapper extends BaseMapper<Advertisement> {

    /** 查询图片广告内容 URL */
    @Select("SELECT content FROM t_advertisement WHERE is_deleted = 0 AND type = 'image' AND content IS NOT NULL AND content <> ''")
    java.util.List<String> selectImageContentUrls();

    /** 查询图片广告 id、title、content（用于引用来源检测） */
    @Select("SELECT id, title, content FROM t_advertisement WHERE is_deleted = 0 AND type = 'image'")
    java.util.List<java.util.Map<String, Object>> selectImageAdSummaries();

    /** 分页查询已软删除的广告 */
    @Select("<script>" +
            "SELECT * FROM t_advertisement WHERE is_deleted = 1" +
            "<if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            " ORDER BY update_time DESC" +
            "</script>")
    IPage<Advertisement> selectDeletedPage(Page<Advertisement> page, @Param("keyword") String keyword);

    /** 恢复软删除的广告 */
    @Update("UPDATE t_advertisement SET is_deleted = 0, status = 'expired' WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    /** 物理删除广告 */
    @Delete("DELETE FROM t_advertisement WHERE id = #{id} AND is_deleted = 1")
    int permanentDeleteById(@Param("id") Long id);

    /** 查询所有已删除广告的ID */
    @Select("SELECT id FROM t_advertisement WHERE is_deleted = 1")
    java.util.List<Long> selectDeletedIds();
}
