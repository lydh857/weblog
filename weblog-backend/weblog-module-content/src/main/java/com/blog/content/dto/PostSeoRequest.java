package com.blog.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章SEO设置请求
 */
@Data
@Schema(description = "文章SEO设置请求")
public class PostSeoRequest {

    @Schema(description = "SEO标题")
    @Size(max = 60, message = "SEO标题最长60字")
    private String seoTitle;

    @Schema(description = "SEO描述")
    @Size(max = 160, message = "SEO描述最长160字")
    private String seoDescription;

    @Schema(description = "SEO关键词（逗号分隔）")
    @Size(max = 200, message = "SEO关键词最长200字")
    private String seoKeywords;
}
