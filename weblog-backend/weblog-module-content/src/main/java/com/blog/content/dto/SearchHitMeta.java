package com.blog.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "搜索命中扩展元信息")
public class SearchHitMeta {

    private Long id;

    private String categoryName;

    private String subCategoryName;

    private String authorNickname;

    private Integer viewCount;

    private Integer likeCount;

    private Integer collectCount;

    private Integer commentCount;

    private LocalDateTime createTime;
}
