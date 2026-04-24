package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "草稿推送结果上报响应")
public class CrawlerPushResultUpsertResponse {

    @Schema(description = "推送记录 ID")
    private Long pushRecordId;

    @Schema(description = "推送状态")
    private String status;

    @Schema(description = "目标草稿 ID")
    private Long targetDraftId;
}
