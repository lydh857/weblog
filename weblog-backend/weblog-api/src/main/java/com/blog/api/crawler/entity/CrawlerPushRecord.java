package com.blog.api.crawler.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_crawler_push_record")
public class CrawlerPushRecord extends BaseEntityNoDelete {

    private Long candidateId;

    private String pushIdempotencyKey;

    private String deviceId;

    private String status;

    private Long targetDraftId;

    private String message;

    private Integer retryCount;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime pushedAt;
}
