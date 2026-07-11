package com.blog.infra.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 配置实体（单行结构化存储）
 */
@Data
@TableName("t_ai_config")
public class AiConfig implements Serializable {

  @TableId(type = IdType.AUTO)
  private Long id;

  // ===== 连接参数（修改后自动热重载） =====

  /** 全局开关 */
  private Boolean enabled;

  /** 模型提供商 */
  private String provider;

  /** API Key */
  private String apiKey;

  /** API Base URL */
  private String baseUrl;

  /** 对话模型名称 */
  private String model;

  // ===== 运行时参数（修改后立即生效） =====

  /** 单次最大 token 数 */
  private Integer maxTokens;

  /** 超时时间（秒） */
  private Integer timeout;

  /** 月度 token 上限（0=不限制） */
  private Long monthlyTokenLimit;

  // ===== 功能开关 =====

  /** 写作助手开关 */
  private Boolean featureWriting;

  /** 元信息生成开关 */
  private Boolean featureMeta;

  /** AI 问答开关 */
  private Boolean featureChat;

  private LocalDateTime createTime;

  private LocalDateTime updateTime;
}
