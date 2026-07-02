package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "采集候选内容批量入库请求")
public class CrawlerCandidateIngestRequest {

    @NotBlank(message = "idempotencyKey 不能为空")
    @Schema(description = "批次幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String idempotencyKey;

    @NotBlank(message = "workerRunId 不能为空")
    @Schema(description = "本地 worker 运行批次 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workerRunId;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Valid
    @NotEmpty(message = "items 不能为空")
    @Schema(description = "候选内容列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CrawlerCandidateItemDTO> items;
}
