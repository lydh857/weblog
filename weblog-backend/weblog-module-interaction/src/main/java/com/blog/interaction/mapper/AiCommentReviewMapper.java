package com.blog.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.interaction.entity.AiCommentReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 评论审核记录 Mapper
 */
@Mapper
public interface AiCommentReviewMapper extends BaseMapper<AiCommentReview> {
}
