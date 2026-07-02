package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章内容实体（与 t_post 一对一，拆分大字段）
 */
@Data
@TableName("t_post_content")
public class PostContent implements Serializable {

    /** 与 t_post.id 一致 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** Markdown 原始内容 */
    private String content;

    /** 渲染后的 HTML */
    private String htmlContent;

    private LocalDateTime updateTime;
}
