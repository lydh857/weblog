package com.blog.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.content.entity.PostEmbedding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章 Embedding Mapper
 */
@Mapper
public interface PostEmbeddingMapper extends BaseMapper<PostEmbedding> {
}
