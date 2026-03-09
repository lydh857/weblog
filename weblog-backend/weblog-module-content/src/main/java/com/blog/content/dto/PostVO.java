package com.blog.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章视图对象
 */
@Data
@Schema(description = "文章详情")
public class PostVO {

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String coverImage;
    private Long categoryId;
    private String categoryName;
    private Long subCategoryId;
    private String subCategoryName;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private String status;
    private String publishType;
    private LocalDateTime scheduledTime;
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private String previewTheme;
    private String codeTheme;
    private Boolean isTop;
    private Boolean isDisabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 文章内容（详情页才返回） */
    private String content;
    private String htmlContent;

    /** 标签列表 */
    private List<TagVO> tags;

    @Data
    public static class TagVO {
        private Long id;
        private String name;
        private String slug;
        private String color;
    }
}
