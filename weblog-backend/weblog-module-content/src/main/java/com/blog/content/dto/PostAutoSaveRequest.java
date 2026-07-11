package com.blog.content.dto;

import com.blog.infra.security.xss.XssRawDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章自动保存请求（轻量级，只保存标题和内容）
 */
@Data
@Schema(description = "文章自动保存请求")
public class PostAutoSaveRequest {

    @Schema(description = "文章标题")
    @Size(max = 200, message = "标题最长200字")
    private String title;

    @Schema(description = "文章内容（Markdown）")
    @JsonDeserialize(using = XssRawDeserializer.class)
    private String content;
}
