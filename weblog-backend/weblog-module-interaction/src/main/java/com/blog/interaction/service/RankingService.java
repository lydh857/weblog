package com.blog.interaction.service;

import com.blog.interaction.mapper.PostRankingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 排行榜查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final PostRankingMapper postRankingMapper;

    /** 日榜=1, 周榜=2, 月榜=3, 总榜=4 */
    public static final int RANK_DAY = 1;
    public static final int RANK_WEEK = 2;
    public static final int RANK_MONTH = 3;
    public static final int RANK_TOTAL = 4;
    private static final int MAX_DAY_FALLBACK_DAYS = 7;

    public record RankingQueryResult(
            List<Map<String, Object>> items,
            int requestedRankType,
            int servedRankType,
            LocalDate servedStatDate,
            boolean fallbackUsed,
            String fallbackReason
    ) {
    }

    /**
     * 查询排行榜
     * @param rankType 1-日榜 2-周榜 3-月榜 4-总榜
     * @param categoryId 分类ID（null=总榜）
     * @param limit 返回条数
     */
    public List<Map<String, Object>> getRanking(int rankType, Long categoryId, int limit, int offset) {
        LocalDate statDate = resolveStatDate(rankType);
        return postRankingMapper.selectRankingWithPost(rankType, categoryId, statDate, limit, offset);
    }

    /**
     * 智能查询排行榜：当日榜无数据时自动回退，避免首页出现空榜
     */
    public RankingQueryResult getRankingSmart(int rankType, Long categoryId, int limit, int offset) {
        LocalDate statDate = resolveStatDate(rankType);
        List<Map<String, Object>> items = queryRanking(rankType, categoryId, statDate, limit, offset);
        if (rankType != RANK_DAY || !items.isEmpty()) {
            return new RankingQueryResult(items, rankType, rankType, statDate, false, null);
        }

        LocalDate today = LocalDate.now();
        for (int i = 1; i <= MAX_DAY_FALLBACK_DAYS; i++) {
            LocalDate fallbackDate = today.minusDays(i);
            List<Map<String, Object>> fallbackItems = queryRanking(RANK_DAY, categoryId, fallbackDate, limit, offset);
            if (!fallbackItems.isEmpty()) {
                String reason = i == 1 ? "daily_empty_fallback_yesterday" : "daily_empty_fallback_recent_day";
                return new RankingQueryResult(fallbackItems, rankType, RANK_DAY, fallbackDate, true, reason);
            }
        }

        LocalDate weekStart = resolveStatDate(RANK_WEEK);
        List<Map<String, Object>> weekItems = queryRanking(RANK_WEEK, categoryId, weekStart, limit, offset);
        if (!weekItems.isEmpty()) {
            return new RankingQueryResult(weekItems, rankType, RANK_WEEK, weekStart, true, "daily_empty_fallback_week");
        }

        List<Map<String, Object>> totalItems = queryRanking(RANK_TOTAL, categoryId, null, limit, offset);
        if (!totalItems.isEmpty()) {
            return new RankingQueryResult(totalItems, rankType, RANK_TOTAL, null, true, "daily_empty_fallback_total");
        }

        return new RankingQueryResult(Collections.emptyList(), rankType, rankType, statDate, true, "daily_empty_no_candidates");
    }

    private List<Map<String, Object>> queryRanking(int rankType, Long categoryId, LocalDate statDate, int limit, int offset) {
        return postRankingMapper.selectRankingWithPost(rankType, categoryId, statDate, limit, offset);
    }

    private LocalDate resolveStatDate(int rankType) {
        LocalDate today = LocalDate.now();
        return switch (rankType) {
            case RANK_DAY -> today;
            case RANK_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case RANK_MONTH -> today.withDayOfMonth(1);
            case RANK_TOTAL -> null; // 总榜 stat_date 为 NULL
            default -> today;
        };
    }
}
