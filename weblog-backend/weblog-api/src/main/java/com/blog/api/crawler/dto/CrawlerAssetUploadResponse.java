package com.blog.api.crawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "采集图片上传响应")
public class CrawlerAssetUploadResponse {

    @Schema(description = "上传后的可访问地址")
    private String url;

    @Schema(description = "对象存储 key")
    private String objectKey;
}
