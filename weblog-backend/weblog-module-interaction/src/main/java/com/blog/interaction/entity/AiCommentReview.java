package com.blog.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 评论审核记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_comment_review")
public class AiCommentReview extends BaseEntityNoDelete {

  /** 评论ID */
  private Long commentId;

  /** 审核结果：pass / suspect / reject */
  private String result;

  /** 审核理由 */
  private String reason;

  /** 置信度 0.00-1.00 */
  private BigDecimal confidence;

  /** 使用的模型 */
  private String model;

  /** 消耗 token 数 */
  private Integer tokenUsed;
}
