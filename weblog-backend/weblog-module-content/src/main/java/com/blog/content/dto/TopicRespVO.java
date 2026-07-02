package com.blog.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专题列表响应 VO
 */
@Data
public class TopicRespVO {

    private Long id;

    private String title;

    private String cover;

    private String summary;

    private Integer weight;

    private Boolean isPublish;

    private Boolean isTop;

    /** 专题内关联的文章总数 */
    private Integer articleCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
