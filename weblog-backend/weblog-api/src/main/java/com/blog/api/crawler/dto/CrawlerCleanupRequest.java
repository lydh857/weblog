package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "采集残留数据清理请求")
public class CrawlerCleanupRequest {

    @Schema(description = "候选保留天数，仅清理 failed/rejected，默认 30")
    private Integer candidateRetentionDays;

    @Schema(description = "推送记录保留天数，默认 90")
    private Integer pushRecordRetentionDays;
}
