package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 外链域名治理策略
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_link_domain_policy")
public class LinkDomainPolicy extends BaseEntityNoDelete {

    /** 归一化后的域名 */
    private String domain;

    /** 状态: pending/trusted/blocked */
    private String status;

    /** 来源: auto/seed/manual */
    private String source;

    /** 审核人标识 */
    private String reviewer;

    /** 审核备注 */
    private String reviewReason;

    /** 过期时间（NULL 表示不过期） */
    private LocalDateTime expireAt;
}
