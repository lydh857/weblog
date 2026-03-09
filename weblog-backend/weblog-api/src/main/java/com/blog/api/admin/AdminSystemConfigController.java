package com.blog.api.admin;

import com.blog.api.scheduler.RankingComputeScheduler;
import com.blog.common.result.Result;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.entity.SystemConfig;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 - 系统配置
 */
@Tag(name = "管理端-系统配置", description = "系统配置查询与更新")
@RestController
@RequestMapping("/api/admin/system-config")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;
    private final RankingComputeScheduler rankingComputeScheduler;

    @Operation(summary = "获取所有系统配置")
    @GetMapping
    public Result<List<SystemConfig>> list() {
        return Result.success(systemConfigService.listAll());
    }

    @Operation(summary = "批量更新系统配置")
    @PutMapping
    @AuditLog(module = "系统配置", operation = "UPDATE", description = "批量更新系统配置")
    public Result<Void> batchUpdate(@RequestBody Map<String, String> configs) {
        systemConfigService.batchUpdate(configs);
        return Result.success();
    }

    @Operation(summary = "手动刷新排行榜")
    @PostMapping("/refresh-ranking")
    @AuditLog(module = "系统配置", operation = "UPDATE", description = "手动刷新排行榜")
    public Result<Void> refreshRanking() {
        rankingComputeScheduler.computeRankings();
        return Result.success();
    }
}
