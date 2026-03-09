package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文章实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_post")
public class Post extends BaseEntity {

    private String title;

    private String slug;

    private String summary;

    private String coverImage;

    private Long categoryId;

    private Long subCategoryId;

    private Long authorId;

    /** draft / published / scheduled */
    private String status;

    /** immediate / scheduled */
    private String publishType;

    private LocalDateTime scheduledTime;

    private Boolean isPublished;

    private Integer viewCount;

    private Integer likeCount;

    private Integer collectCount;

    private Integer commentCount;

    private String seoTitle;

    private String seoDescription;

    private String seoKeywords;

    /** 预览主题 */
    private String previewTheme;

    /** 代码主题 */
    private String codeTheme;

    private Boolean isTop;

    /** 是否禁用（禁用后前台不展示） */
    private Boolean isDisabled;
}
