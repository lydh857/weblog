package com.blog.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "邮箱", example = "user@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "密码（HTTPS 加密传输）", example = "MyPassword123")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "是否记住我（生成 Remember Token）", example = "true", defaultValue = "false")
    private boolean rememberMe;
}
