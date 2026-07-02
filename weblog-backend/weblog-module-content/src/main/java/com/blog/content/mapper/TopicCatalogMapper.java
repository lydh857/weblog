package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.TopicCatalog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 专题目录 Mapper
 */
@Mapper
public interface TopicCatalogMapper extends BaseMapper<TopicCatalog> {

    /** 按 sort 升序返回指定专题的所有目录节点 */
    @Select("SELECT id, topic_id, article_id, title, level, parent_id, sort, is_deleted FROM t_topic_catalog WHERE topic_id = #{topicId} AND is_deleted = 0 ORDER BY sort ASC")
    List<TopicCatalog> selectByTopicId(@Param("topicId") Long topicId);

    /** 查询指定专题目录中所有非 null 的 article_id */
    @Select("SELECT article_id FROM t_topic_catalog WHERE topic_id = #{topicId} AND is_deleted = 0 AND article_id IS NOT NULL")
    List<Long> selectArticleIdsByTopicId(@Param("topicId") Long topicId);

    /** 恢复指定专题的所有已删除目录节点 */
    @Update("UPDATE t_topic_catalog SET is_deleted = 0 WHERE topic_id = #{topicId} AND is_deleted = 1")
    int restoreByTopicId(@Param("topicId") Long topicId);

    /** 批量恢复多个专题的所有已删除目录节点 */
    @Update("<script>" +
            "UPDATE t_topic_catalog SET is_deleted = 0 WHERE is_deleted = 1 AND topic_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchRestoreByTopicIds(@Param("ids") List<Long> ids);

    /** 物理删除指定专题的所有已删除目录节点 */
    @Delete("DELETE FROM t_topic_catalog WHERE topic_id = #{topicId} AND is_deleted = 1")
    int permanentDeleteByTopicId(@Param("topicId") Long topicId);

    /** 批量物理删除多个专题的所有已删除目录节点 */
    @Delete("<script>" +
            "DELETE FROM t_topic_catalog WHERE is_deleted = 1 AND topic_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchPermanentDeleteByTopicIds(@Param("ids") List<Long> ids);
}
