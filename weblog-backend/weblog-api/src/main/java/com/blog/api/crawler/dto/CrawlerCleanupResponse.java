package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "采集残留数据清理响应")
public class CrawlerCleanupResponse {

    private Integer deletedCandidateCount;

    private Integer deletedPushRecordCount;
}
