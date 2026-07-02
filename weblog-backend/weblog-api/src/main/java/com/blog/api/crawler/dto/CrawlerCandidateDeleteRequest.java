package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "删除采集候选请求")
public class CrawlerCandidateDeleteRequest {

    @Schema(description = "是否同时删除关联草稿")
    private Boolean deleteDraft;
}
