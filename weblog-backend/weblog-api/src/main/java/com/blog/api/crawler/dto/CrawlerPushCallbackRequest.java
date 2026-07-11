package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "推送回调状态请求")
public class CrawlerPushCallbackRequest {

    @NotBlank(message = "pushIdempotencyKey 不能为空")
    @Schema(description = "推送幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pushIdempotencyKey;

    @NotBlank(message = "state 不能为空")
    @Schema(description = "回调状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String state;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "状态更新时间")
    private LocalDateTime updatedAt;
}
