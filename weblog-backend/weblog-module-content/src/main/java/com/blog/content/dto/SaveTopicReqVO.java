package com.blog.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建或更新专题请求
 */
@Data
public class SaveTopicReqVO {

    @NotBlank(message = "专题标题不能为空")
    @Size(max = 100, message = "专题标题最长 100 字")
    private String title;

    @Size(max = 500, message = "封面图 URL 最长 500 字")
    private String cover;

    @Size(max = 500, message = "专题摘要最长 500 字")
    private String summary;
}
