package com.blog.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录响应
 * 注意：Token 已改为 HttpOnly Cookie 传输，不再通过响应体返回
 */
@Data
@Builder
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "登录状态，true 表示成功")
    private boolean success;
    @Schema(description = "用户 ID")
    private Long userId;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "头像 URL")
    private String avatar;
    @Schema(description = "角色（user/admin）")
    private String role;
    @Schema(description = "是否需要绑定邮箱")
    private boolean needBindEmail;
    @Schema(description = "是否已设置密码")
    private boolean hasPassword;
    @Schema(description = "Remember Token（仅当 rememberMe=true 时返回）")
    private String rememberToken;
}
