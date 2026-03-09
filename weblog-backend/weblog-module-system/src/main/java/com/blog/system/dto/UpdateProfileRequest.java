package com.blog.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求
 */
@Data
@Schema(description = "更新个人资料请求")
public class UpdateProfileRequest {

    @Schema(description = "昵称", example = "张三")
    @Size(min = 1, max = 50, message = "昵称长度1-50个字符")
    private String nickname;

    @Schema(description = "个人简介", example = "一个热爱编程的开发者")
    @Size(max = 500, message = "简介最多500个字符")
    private String bio;
}
