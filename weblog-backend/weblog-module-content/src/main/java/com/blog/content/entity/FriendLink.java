package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 友链实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_friend_link")
public class FriendLink extends BaseEntityNoDelete {

    /** 网站名称 */
    private String name;

    /** 网站链接 */
    private String url;

    /** Logo URL */
    private String logo;

    /** 网站描述 */
    private String description;

    /** 状态: active/inactive/broken/pending/rejected */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 申请人用户ID */
    private Long applicantUserId;

    /** 审批原因（拒绝时填写） */
    private String reason;

    /** 最后检测时间 */
    private LocalDateTime lastCheckTime;
}
