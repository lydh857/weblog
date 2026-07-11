package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "采集候选内容批量入库响应")
public class CrawlerCandidateIngestResponse {

    @Schema(description = "后端入库请求 ID")
    private String ingestRequestId;

    @Schema(description = "接收成功条目")
    private List<CrawlerIngestAcceptedItemDTO> accepted;

    @Schema(description = "拒绝条目")
    private List<CrawlerIngestRejectedItemDTO> rejected;
}
