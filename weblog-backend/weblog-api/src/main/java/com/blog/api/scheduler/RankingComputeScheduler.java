package com.blog.api.scheduler;

import com.blog.content.mapper.PostMapper;
import com.blog.interaction.entity.PostRanking;
import com.blog.interaction.mapper.PostRankingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * 排行榜计算定时任务
 * 每小时计算一次排行榜数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingComputeScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final PostRankingMapper postRankingMapper;

    /**
     * 每小时第30分钟执行（错开阅读数同步的整点）
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(cron = "0 30 * * * ?")
    public synchronized void computeRankings() {
        log.info("开始计算排行榜...");
        try {
            LocalDate today = LocalDate.now();
            // 总榜
            computeRank(4, null, null, null);
            // 日榜
            computeRank(1, today, today, today);
            // 周榜
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            computeRank(2, weekStart, today, weekStart);
            // 月榜
            LocalDate monthStart = today.withDayOfMonth(1);
            computeRank(3, monthStart, today, monthStart);
            log.info("排行榜计算完成");
        } catch (Exception e) {
            log.error("排行榜计算失败: {}", e.getMessage(), e);
        }
    }

    private void computeRank(int rankType, LocalDate dateFrom, LocalDate dateTo, LocalDate statDate) {
        List<Map<String, Object>> rows;
        if (rankType == 4) {
            // 总榜：直接用 t_post 的累计数据
            rows = jdbcTemplate.queryForList("""
                SELECT p.id AS post_id, p.category_id,
                       (p.view_count + p.like_count * 2 + p.collect_count * 3) AS score
                FROM t_post p
                WHERE p.is_deleted = 0 AND p.status = 'published'
                ORDER BY score DESC
                LIMIT 100
                """);
        } else {
            // 日/周/月榜：用 t_post_read_daily 的区间数据 + 基础互动数据
            rows = jdbcTemplate.queryForList("""
                SELECT p.id AS post_id, p.category_id,
                       (COALESCE(SUM(d.read_count), 0) + p.like_count * 2 + p.collect_count * 3) AS score
                FROM t_post p
                LEFT JOIN t_post_read_daily d ON p.id = d.post_id AND d.read_date BETWEEN ? AND ?
                WHERE p.is_deleted = 0 AND p.status = 'published'
                GROUP BY p.id
                HAVING score > 0
                ORDER BY score DESC
                LIMIT 100
                """, dateFrom, dateTo);
        }

        // 清除旧排行数据
        if (rankType == 4) {
            jdbcTemplate.update("DELETE FROM t_post_ranking WHERE rank_type = 4 AND stat_date IS NULL AND category_id IS NULL");
        } else {
            jdbcTemplate.update("DELETE FROM t_post_ranking WHERE rank_type = ? AND stat_date = ? AND category_id IS NULL",
                rankType, statDate);
        }

        // 插入新排行
        int rank = 1;
        for (Map<String, Object> row : rows) {
            PostRanking pr = new PostRanking();
            pr.setPostId(((Number) row.get("post_id")).longValue());
            pr.setCategoryId(null); // 总榜
            pr.setRankType(rankType);
            pr.setRankNum(rank++);
            pr.setScore(((Number) row.get("score")).intValue());
            pr.setStatDate(statDate);
            postRankingMapper.insert(pr);
        }
    }
}
