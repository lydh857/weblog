package com.blog.api.crawler.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_crawler_candidate")
public class CrawlerCandidate extends BaseEntityNoDelete {

    private String requestIdempotencyKey;

    private String itemIdempotencyKey;

    private String workerRunId;

    private String deviceId;

    private String externalUrl;

    private String normalizedUrl;

    private String sourceSite;

    private String title;

    private String summary;

    private String contentMarkdown;

    private String coverImage;

    private String imageRefsJson;

    private String contentFingerprint;

    private LocalDateTime publishedAt;

    private String author;

    private String metadataJson;

    private String state;

    private Long targetDraftId;

    private String pushMessage;

    private LocalDateTime lastPushedAt;
}
