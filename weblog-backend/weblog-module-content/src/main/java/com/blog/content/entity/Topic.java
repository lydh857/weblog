package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 专题实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_topic")
public class Topic extends BaseEntity {

    /** 专题标题 */
    private String title;

    /** 封面图 URL */
    private String cover;

    /** 专题摘要 */
    private String summary;

    /** 排序权重，越大越靠前 */
    private Integer weight;

    /** 是否已发布 */
    private Boolean isPublish;

    /** 是否置顶 */
    private Boolean isTop;
}
