package com.blog.content.dto;

import lombok.Data;

/**
 * 专题分页查询请求
 */
@Data
public class TopicPageReqVO {

    /** 页码，默认 1 */
    private Integer pageNum = 1;

    /** 每页条数，默认 10 */
    private Integer pageSize = 10;

    /** 标题关键词（模糊搜索） */
    private String keyword;

    /** 发布状态筛选，null 表示不筛选 */
    private Boolean isPublish;

    /** 置顶状态筛选，null 表示不筛选 */
    private Boolean isTop;
}
