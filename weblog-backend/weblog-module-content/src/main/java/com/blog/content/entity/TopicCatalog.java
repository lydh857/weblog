package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 专题目录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_topic_catalog")
public class TopicCatalog extends BaseEntity {

    /** 所属专题 ID */
    private Long topicId;

    /** 关联文章 ID，null 表示纯目录节点 */
    private Long articleId;

    /** 目录节点标题 */
    private String title;

    /** 层级深度，从 1 开始 */
    private Integer level;

    /** 父节点 ID，0 表示顶级 */
    private Long parentId;

    /** 同级排序值，越小越靠前 */
    private Integer sort;
}
