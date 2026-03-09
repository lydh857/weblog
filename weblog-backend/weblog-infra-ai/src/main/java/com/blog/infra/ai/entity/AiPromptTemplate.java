package com.blog.infra.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 提示词模板实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_prompt_template")
public class AiPromptTemplate extends BaseEntity {

    /** 模板标识（如 writing_continue, meta_summary） */
    private String templateKey;

    /** 模板名称 */
    private String name;

    /** 用途说明 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户提示词模板（含变量占位符） */
    private String userPromptTemplate;

    /** 可用变量列表（JSON数组） */
    private String variables;

    /** 是否已自定义（区分默认/自定义） */
    private Boolean isCustomized;
}
