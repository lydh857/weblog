package com.blog.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索结果
 */
@Data
@Schema(description = "搜索结果")
public class SearchResult {

    @Schema(description = "总命中数")
    private long total;

    @Schema(description = "搜索结果列表")
    private List<SearchHit> hits;

    @Data
    @Schema(description = "搜索命中项")
    public static class SearchHit {
        private Long id;
        private String title;
        private String slug;
        private String summary;
        /** 高亮后的标题片段 */
        private String highlightTitle;
        /** 高亮后的内容片段 */
        private String highlightContent;
        private Long categoryId;
        private Long authorId;
        private String categoryName;
        private String subCategoryName;
        private String authorNickname;
        private Integer viewCount;
        private Integer likeCount;
        private Integer collectCount;
        private Integer commentCount;
        private LocalDateTime createTime;
    }

}
