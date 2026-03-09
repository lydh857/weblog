package com.blog.infra.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.infra.ai.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 提示词模板 Mapper
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
