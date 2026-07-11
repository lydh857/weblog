package com.blog.infra.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.infra.ai.entity.AiConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 配置 Mapper
 */
@Mapper
public interface AiConfigMapper extends BaseMapper<AiConfig> {
}
