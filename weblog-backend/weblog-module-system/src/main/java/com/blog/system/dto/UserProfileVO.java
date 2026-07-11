package com.blog.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户个人资料 VO
 */
@Data
@Builder
@Schema(description = "用户个人资料")
public class UserProfileVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "个人简介")
    private String bio;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "是否已设置密码")
    private boolean hasPassword;

    @Schema(description = "是否需要绑定邮箱（邮箱为空）")
    private boolean needBindEmail;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    @Schema(description = "个人信息审核状态（pending/approved/rejected）")
    private String profileReviewStatus;

    @Schema(description = "审核拒绝原因")
    private String profileReviewRejectReason;

    @Schema(description = "审核提交时间")
    private LocalDateTime profileReviewSubmitTime;

    @Schema(description = "待审核昵称")
    private String pendingNickname;

    @Schema(description = "待审核简介")
    private String pendingBio;

    @Schema(description = "待审核头像URL")
    private String pendingAvatar;
}
