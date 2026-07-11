package com.blog.api.crawler.model;

public enum CrawlerTaskItemState {
    QUEUED,
    CRAWLING,
    PARSED,
    REVIEW_PENDING,
    APPROVED,
    REJECTED,
    FAILED,
    PUSHED
}
