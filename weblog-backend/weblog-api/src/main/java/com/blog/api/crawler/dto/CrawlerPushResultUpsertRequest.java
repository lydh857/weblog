package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "草稿推送结果上报请求")
public class CrawlerPushResultUpsertRequest {

    @NotBlank(message = "pushIdempotencyKey 不能为空")
    @Schema(description = "推送幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pushIdempotencyKey;

    @NotNull(message = "candidateId 不能为空")
    @Schema(description = "候选内容 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long candidateId;

    @Schema(description = "目标草稿 ID")
    private Long targetDraftId;

    @NotBlank(message = "status 不能为空")
    @Schema(description = "推送状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @Schema(description = "状态说明")
    private String message;

    @Schema(description = "推送完成时间")
    private LocalDateTime pushedAt;
}
