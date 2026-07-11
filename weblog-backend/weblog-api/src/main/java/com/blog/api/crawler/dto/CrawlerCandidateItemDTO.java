package com.blog.api.crawler.dto;

import com.blog.infra.security.xss.XssRawDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "采集候选内容条目")
public class CrawlerCandidateItemDTO {

    @NotBlank(message = "itemIdempotencyKey 不能为空")
    @Schema(description = "条目幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemIdempotencyKey;

    @NotBlank(message = "externalUrl 不能为空")
    @Schema(description = "原始来源 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String externalUrl;

    @NotBlank(message = "normalizedUrl 不能为空")
    @Schema(description = "规范化 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String normalizedUrl;

    @Schema(description = "来源站点")
    private String sourceSite;

    @NotBlank(message = "title 不能为空")
    @Schema(description = "文章标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "文章摘要")
    private String summary;

    @NotBlank(message = "contentMarkdown 不能为空")
    @Schema(description = "正文 Markdown", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = XssRawDeserializer.class)
    private String contentMarkdown;

    @Schema(description = "封面图路径/URL")
    private String coverImage;

    @Schema(description = "正文图片引用")
    private List<String> imageRefs;

    @Schema(description = "内容指纹")
    private String contentFingerprint;

    @Schema(description = "原文发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "原文作者")
    private String author;

    @Schema(description = "扩展元数据")
    private Map<String, Object> metadata;
}
