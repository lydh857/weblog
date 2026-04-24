package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "候选推送草稿单条结果")
public class CrawlerDraftPushItemResultDTO {

    @Schema(description = "候选 ID")
    private Long candidateId;

    @Schema(description = "推送状态 succeeded/failed")
    private String status;

    @Schema(description = "草稿 ID")
    private Long draftId;

    @Schema(description = "结果消息")
    private String message;

    @Schema(description = "真实推送时间")
    private LocalDateTime pushedAt;
}
