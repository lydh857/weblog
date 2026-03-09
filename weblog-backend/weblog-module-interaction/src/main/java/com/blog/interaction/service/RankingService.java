package com.blog.interaction.service;

import com.blog.interaction.mapper.PostRankingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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
