package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "采集入库拒绝条目")
public class CrawlerIngestRejectedItemDTO {

    @Schema(description = "条目幂等键")
    private String itemIdempotencyKey;

    @Schema(description = "拒绝原因码")
    private String reasonCode;

    @Schema(description = "拒绝原因说明")
    private String reasonMessage;

    @Schema(description = "命中的重复候选 ID")
    private Long duplicateCandidateId;

    @Schema(description = "命中的重复候选标题")
    private String duplicateTitle;

    @Schema(description = "命中的重复候选原始链接")
    private String duplicateExternalUrl;

    @Schema(description = "命中的重复候选标准化链接")
    private String duplicateNormalizedUrl;

    @Schema(description = "命中的重复候选最近推送时间")
    private LocalDateTime duplicateLastPushedAt;

    @Schema(description = "命中的重复候选草稿 ID")
    private Long duplicateTargetDraftId;
}
