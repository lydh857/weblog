package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Advertisement;
import com.blog.content.service.AdvertisementService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;
import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 管理端 - 广告管理
 */
@Tag(name = "管理端-广告管理", description = "广告审核、上下架、配置")
@RestController
@RequestMapping("/api/admin/advertisement")
@RequiredArgsConstructor
public class AdminAdvertisementController {

    private final AdvertisementService advertisementService;
    private final SystemConfigService systemConfigService;
    private static final Set<String> VALID_AD_STATUSES = Set.of("pending", "approved", "rejected", "active", "expired");

    @Operation(summary = "广告列表（分页）")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String position) {
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        IPage<Advertisement> page = advertisementService.listPage(pageNum, pageSize, status, position);
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "创建广告")
    @PostMapping
    @AuditLog(module = "广告管理", operation = "CREATE", description = "创建广告")
    public Result<Advertisement> create(@RequestBody Advertisement ad) {
        // 管理员创建的广告无需审核，直接投放
        ad.setStatus("active");
        return Result.success(advertisementService.create(ad));
    }

    @Operation(summary = "更新广告")
    @PutMapping("/{id}")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "更新广告")
    public Result<Void> update(@PathVariable Long id, @RequestBody Advertisement ad) {
        advertisementService.update(id, ad);
        return Result.success();
    }

    @Operation(summary = "审核/上下架广告")
    @PutMapping("/{id}/status")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "审核广告")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        if (!VALID_AD_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, active, expired");
        }
        advertisementService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除广告")
    @DeleteMapping("/{id}")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "删除广告")
    public Result<Void> delete(@PathVariable Long id) {
        advertisementService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除广告")
    @DeleteMapping("/batch")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "批量删除广告")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > MAX_BATCH_SIZE) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
            }
            advertisementService.batchDelete(ids);
        }
        return Result.success();
    }

    @Operation(summary = "批量上下架广告")
    @PutMapping("/batch/status")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "批量上下架广告")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        String status = (String) body.get("status");
        if (status != null && !VALID_AD_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, active, expired");
        }
        if (ids != null && !ids.isEmpty() && status != null) {
            advertisementService.batchUpdateStatus(
                ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList()),
                status);
        }
        return Result.success();
    }

    @Operation(summary = "获取广告申请开关状态")
    @GetMapping("/apply-switch")
    public Result<Map<String, Object>> getApplySwitch() {
        String val = systemConfigService.getValue("ad_apply_enabled");
        return Result.success(Map.of("enabled", "true".equals(val)));
    }

    @Operation(summary = "设置广告申请开关")
    @PutMapping("/apply-switch")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "设置广告申请开关")
    public Result<Void> setApplySwitch(@RequestBody Map<String, Object> body) {
        Boolean enabled = (Boolean) body.get("enabled");
        String val = enabled != null && enabled ? "true" : "false";
        try {
            systemConfigService.batchUpdate(Map.of("ad_apply_enabled", val));
        } catch (Exception e) {
            // 配置项不存在时，尝试创建
            systemConfigService.createIfAbsent("ad_apply_enabled", val, "广告申请入口开关");
        }
        return Result.success();
    }

    // ========== 回收站 ==========

    @Operation(summary = "回收站列表（分页）")
    @GetMapping("/trash")
    public Result<Map<String, Object>> trashPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        var page = advertisementService.pageDeleted(pageNum, pageSize, keyword);
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "批量恢复广告")
    @PutMapping("/trash/batch-restore")
    @AuditLog(module = "广告管理", operation = "UPDATE", description = "批量恢复广告")
    public Result<Integer> batchRestore(@RequestBody List<Long> ids) {
        return Result.success(advertisementService.batchRestore(ids));
    }

    @Operation(summary = "批量永久删除广告")
    @DeleteMapping("/trash/batch-permanent")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "批量永久删除广告")
    public Result<Integer> batchPermanentDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        return Result.success(advertisementService.batchPermanentDelete(
                ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList())));
    }

    @Operation(summary = "清空回收站")
    @DeleteMapping("/trash/clear")
    @AuditLog(module = "广告管理", operation = "DELETE", description = "清空广告回收站")
    public Result<Integer> clearTrash() {
        return Result.success(advertisementService.clearTrash());
    }
}
