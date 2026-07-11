package com.blog.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录日志 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录日志")
public class LoginLogVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户邮箱")
    private String email;

    @Schema(description = "登录类型: user, admin")
    private String loginType;

    @Schema(description = "登录结果: success, failed")
    private String result;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "User-Agent")
    private String userAgent;

    @Schema(description = "登录时间")
    private LocalDateTime createTime;
}
