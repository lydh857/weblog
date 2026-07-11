package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "候选内容推送草稿请求")
public class CrawlerDraftPushRequest {

    @NotEmpty(message = "candidateIds 不能为空")
    @Schema(description = "待推送候选 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> candidateIds;

    @Schema(description = "重复内容处理模式：skip/update_existing_draft/create_new_draft")
    private String pushMode;
}
