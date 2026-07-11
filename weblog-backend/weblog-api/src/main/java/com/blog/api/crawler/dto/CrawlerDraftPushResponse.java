package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "候选内容推送草稿响应")
public class CrawlerDraftPushResponse {

    @Schema(description = "总条数")
    private Integer total;

    @Schema(description = "成功条数")
    private Integer succeeded;

    @Schema(description = "失败条数")
    private Integer failed;

    @Schema(description = "逐条结果")
    private List<CrawlerDraftPushItemResultDTO> results;
}
