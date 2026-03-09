package com.blog.infra.ai.dto;

import lombok.Data;

import java.util.Map;

/**
 * Token 用量统计 VO
 */
@Data
public class TokenUsageVO {

    /** 月份（yyyy-MM） */
    private String month;

    /** 总输入 token */
    private long totalInput;

    /** 总输出 token */
    private long totalOutput;

    /** 各功能用量明细 */
    private Map<String, FeatureUsage> featureBreakdown;

    @Data
    public static class FeatureUsage {
        private long inputTokens;
        private long outputTokens;
    }
}
