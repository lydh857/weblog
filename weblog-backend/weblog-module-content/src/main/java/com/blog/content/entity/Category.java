package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_category")
public class Category extends BaseEntityNoDelete {

    private String name;

    private String slug;

    private String description;

    /** 父分类ID，0为顶级 */
    private Long parentId;

    private Integer sortOrder;
}
