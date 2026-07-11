package com.blog.infra.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntityNoDelete;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI Token 用量日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_token_log")
public class AiTokenLog extends BaseEntityNoDelete {

    /** 功能标识（writing/meta/review/chat） */
    private String feature;

    /** 输入 token 数 */
    private Integer inputTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 使用的模型 */
    private String model;
}
