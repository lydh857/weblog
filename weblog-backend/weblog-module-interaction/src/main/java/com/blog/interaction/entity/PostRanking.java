package com.blog.interaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文章排行榜
 */
@Data
@TableName("t_post_ranking")
public class PostRanking implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    /** NULL表示总榜，有值表示分类榜 */
    private Long categoryId;

    /** 1-日榜 2-周榜 3-月榜 4-总榜 */
    private Integer rankType;

    private Integer rankNum;

    /** 排序分数（阅读+点赞*2+收藏*3） */
    private Integer score;

    /** 统计日期（日周月榜用，总榜为NULL） */
    private LocalDate statDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
