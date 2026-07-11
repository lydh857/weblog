package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章标签关联实体（物理删除，避免唯一索引冲突）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_post_tag")
public class PostTag extends BaseEntityNoDelete {

    private Long postId;

    private Long tagId;
}
