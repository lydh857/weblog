package com.blog.infra.ai.dto;

import lombok.Data;

import java.util.List;
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

    /** 总 token（输入 + 输出） */
    private long totalTokens;

    /** 当月请求总次数 */
    private long totalRequests;

    /** 今日输入 token */
    private long todayInput;

    /** 今日输出 token */
    private long todayOutput;

    /** 今日 token（输入 + 输出） */
    private long todayTokens;

    /** 今日请求次数 */
    private long todayRequests;

    /** 月度上限（0 表示不限制） */
    private long monthlyLimit;

    /** 月度上限使用率（0~100+） */
    private double limitUsagePercent;

    /** 月内按天趋势 */
    private List<DailyUsage> dailyTrend;

    /** 各功能用量明细 */
    private Map<String, FeatureUsage> featureBreakdown;

    @Data
    public static class DailyUsage {
        private String date;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;
        private long requests;
    }

    @Data
    public static class FeatureUsage {
        private long inputTokens;
        private long outputTokens;
    }
}
