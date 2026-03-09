package com.blog.content.dto;

import com.blog.infra.security.xss.XssRawDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建/更新文章请求
 */
@Data
@Schema(description = "创建/更新文章请求")
public class PostCreateRequest {

    @Schema(description = "文章标题")
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长200字")
    private String title;

    @Schema(description = "URL标识（留空自动生成）")
    @Size(max = 80, message = "Slug最长80字")
    private String slug;

    @Schema(description = "文章内容（Markdown）")
    @NotBlank(message = "内容不能为空")
    @JsonDeserialize(using = XssRawDeserializer.class)
    private String content;

    @Schema(description = "文章摘要")
    @Size(max = 500, message = "摘要最长500字")
    private String summary;

    @Schema(description = "封面图URL")
    private String coverImage;

    @Schema(description = "一级分类ID")
    private Long categoryId;

    @Schema(description = "二级分类ID")
    private Long subCategoryId;

    @Schema(description = "标签ID列表（已有标签）")
    private List<Long> tagIds;

    @Schema(description = "新标签名称列表（发布时自动创建）")
    private List<String> newTagNames;

    @Schema(description = "新分类名称（发布时自动创建）")
    private String newCategoryName;

    @Schema(description = "新分类的父分类ID（0为一级分类）")
    private Long newCategoryParentId;

    @Schema(description = "状态: draft/published/scheduled")
    private String status;

    @Schema(description = "定时发布时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "SEO标题")
    @Size(max = 60, message = "SEO标题最长60字")
    private String seoTitle;

    @Schema(description = "SEO描述")
    @Size(max = 160, message = "SEO描述最长160字")
    private String seoDescription;

    @Schema(description = "SEO关键词")
    @Size(max = 200, message = "SEO关键词最长200字")
    private String seoKeywords;

    @Schema(description = "预览主题")
    @Size(max = 20, message = "预览主题最长20字")
    private String previewTheme;

    @Schema(description = "代码主题")
    @Size(max = 20, message = "代码主题最长20字")
    private String codeTheme;

    @Schema(description = "是否置顶")
    private Boolean isTop;
}
