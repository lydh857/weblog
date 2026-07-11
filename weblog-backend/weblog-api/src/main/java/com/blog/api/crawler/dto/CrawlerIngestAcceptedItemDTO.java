package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "采集入库接收成功条目")
public class CrawlerIngestAcceptedItemDTO {

    @Schema(description = "条目幂等键")
    private String itemIdempotencyKey;

    @Schema(description = "候选内容 ID")
    private Long candidateId;

    @Schema(description = "候选状态")
    private String state;
}
