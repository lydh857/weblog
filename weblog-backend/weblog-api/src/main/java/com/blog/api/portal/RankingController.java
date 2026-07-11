package com.blog.api.portal;

import com.blog.common.result.Result;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.interaction.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 用户端排行榜接口
 */
@Tag(name = "用户端-排行榜", description = "文章排行榜查询")
@RestController
@RequestMapping("/api/portal/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "查询排行榜")
    @GetMapping
    @RateLimit(key = "portal-ranking", capacity = 120, seconds = 60)
    public Result<List<Map<String, Object>>> getRanking(
            @Parameter(description = "排行类型：1-日榜 2-周榜 3-月榜 4-总榜")
            @RequestParam(defaultValue = "4") int rankType,
            @Parameter(description = "分类ID（不传=总榜）")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "返回条数")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量")
            @RequestParam(defaultValue = "0") int offset) {
        if (limit > 100) limit = 100;
        if (offset < 0) offset = 0;
        return Result.success(rankingService.getRanking(rankType, categoryId, limit, offset));
    }

    @Operation(summary = "查询智能排行榜（含回退元信息）")
    @GetMapping("/smart")
    @RateLimit(key = "portal-ranking-smart", capacity = 120, seconds = 60)
    public Result<Map<String, Object>> getRankingSmart(
            @Parameter(description = "排行类型：1-日榜 2-周榜 3-月榜 4-总榜")
            @RequestParam(defaultValue = "4") int rankType,
            @Parameter(description = "分类ID（不传=总榜）")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "返回条数")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "偏移量")
            @RequestParam(defaultValue = "0") int offset) {
        if (limit > 100) limit = 100;
        if (offset < 0) offset = 0;

        RankingService.RankingQueryResult result = rankingService.getRankingSmart(rankType, categoryId, limit, offset);
        Map<String, Object> meta = new HashMap<>();
        meta.put("requestedRankType", result.requestedRankType());
        meta.put("servedRankType", result.servedRankType());
        meta.put("servedStatDate", result.servedStatDate() == null ? null : result.servedStatDate().toString());
        meta.put("fallbackUsed", result.fallbackUsed());
        meta.put("fallbackReason", result.fallbackReason());

        Map<String, Object> data = new HashMap<>();
        data.put("items", result.items());
        data.put("meta", meta);
        return Result.success(data);
    }
}
