package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_announcement")
public class Announcement extends BaseEntityNoDelete {

    private String title;

    /** 公告内容（HTML） */
    private String content;

    /** 公告类型: popup/banner */
    private String type;

    /** 状态: draft/published/archived */
    private String status;

    /** 优先级（越大越优先） */
    private Integer priority;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 是否可关闭 */
    private Boolean isClosable;
}
