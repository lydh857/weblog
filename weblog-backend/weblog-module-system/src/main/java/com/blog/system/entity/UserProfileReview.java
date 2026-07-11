package com.blog.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户个人信息审核实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_profile_review")
public class UserProfileReview extends BaseEntity {

    private Long userId;

    private String pendingNickname;

    private String pendingBio;

    private String pendingAvatar;

    /** pending / approved / rejected */
    private String status;

    private String rejectReason;

    private Long reviewerId;

    private LocalDateTime reviewTime;
}
