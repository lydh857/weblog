package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 广告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_advertisement")
public class Advertisement extends BaseEntity {

    private String title;

    /** 广告类型: image/code */
    private String type;

    /** 广告内容（图片URL或HTML代码） */
    private String content;

    /** 跳转链接 */
    private String linkUrl;

    /** 广告位置: top/middle/bottom/sidebar */
    private String position;

    /** 广告申请者用户ID */
    private Long advertiserId;

    /** 状态: pending/approved/rejected/active/expired */
    private String status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer clickCount;
    private Integer weight;
}
