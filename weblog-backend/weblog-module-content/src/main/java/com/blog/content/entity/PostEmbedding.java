package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章 Embedding 向量实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_post_embedding")
public class PostEmbedding extends BaseEntityNoDelete {

  /** 文章ID */
  private Long postId;

  /** Embedding 向量（float[] 序列化） */
  private byte[] embedding;

  /** 向量维度 */
  private Integer dimension;

  /** 生成模型 */
  private String model;
}
