package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.result.Result;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.common.util.PageParamUtil;
import com.blog.content.entity.Announcement;
import com.blog.content.service.AnnouncementService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;

/**
 * 管理端 - 公告管理
 */
@Tag(name = "管理端-公告管理", description = "公告发布、编辑、审核")
@RestController
@RequestMapping("/api/admin/announcement")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
    private static final Set<String> VALID_ANNOUNCEMENT_STATUSES = Set.of("draft", "published", "archived");
    private static final String ADMIN_ANNOUNCEMENT_DELETE_RATE_LIMIT_KEY = "admin_announcement_delete_rate_limit";
    private static final String ADMIN_ANNOUNCEMENT_STATUS_UPDATE_RATE_LIMIT_KEY = "admin_announcement_status_update_rate_limit";

    @Operation(summary = "公告列表（分页）")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        IPage<Announcement> page = announcementService.listPage(pageParams.pageNum(), pageParams.pageSize(), status, type);
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "创建公告")
    @PostMapping
    @AuditLog(module = "公告管理", operation = "CREATE", description = "创建公告")
    public Result<Announcement> create(@RequestBody Announcement ann) {
        return Result.success(announcementService.create(ann));
    }

    @Operation(summary = "更新公告")
    @PutMapping("/{id}")
    @AuditLog(module = "公告管理", operation = "UPDATE", description = "更新公告")
    public Result<Void> update(@PathVariable Long id, @RequestBody Announcement ann) {
        announcementService.update(id, ann);
        return Result.success();
    }

    @Operation(summary = "更新公告状态")
    @PutMapping("/{id}/status")
    @RateLimit(key = "admin-announcement-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "公告管理", operation = "UPDATE", description = "更新公告状态")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam String status,
                                     HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-announcement-status-update",
                ADMIN_ANNOUNCEMENT_STATUS_UPDATE_RATE_LIMIT_KEY,
                30,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "更新公告状态过于频繁，请稍后再试"
        );
        if (!VALID_ANNOUNCEMENT_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: draft, published, archived");
        }
        announcementService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    @RateLimit(key = "admin-announcement-delete", capacity = 120, seconds = 60)
    @AuditLog(module = "公告管理", operation = "DELETE", description = "删除公告")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-announcement-delete",
                ADMIN_ANNOUNCEMENT_DELETE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "删除公告过于频繁，请稍后再试"
        );
        announcementService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除公告")
    @DeleteMapping("/batch")
    @RateLimit(key = "admin-announcement-delete", capacity = 120, seconds = 60)
    @AuditLog(module = "公告管理", operation = "DELETE", description = "批量删除公告")
    public Result<Void> batchDelete(@RequestBody List<Long> ids, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-announcement-delete",
                ADMIN_ANNOUNCEMENT_DELETE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "批量删除公告过于频繁，请稍后再试"
        );
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > MAX_BATCH_SIZE) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
            }
            announcementService.batchDelete(ids);
        }
        return Result.success();
    }

    @Operation(summary = "批量更新公告状态")
    @PutMapping("/batch/status")
    @RateLimit(key = "admin-announcement-status-update", capacity = 120, seconds = 60)
    @AuditLog(module = "公告管理", operation = "UPDATE", description = "批量更新公告状态")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-announcement-status-update",
                ADMIN_ANNOUNCEMENT_STATUS_UPDATE_RATE_LIMIT_KEY,
                30,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "批量更新公告状态过于频繁，请稍后再试"
        );
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        String status = (String) body.get("status");
        if (status != null && !VALID_ANNOUNCEMENT_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: draft, published, archived");
        }
        if (ids != null && !ids.isEmpty() && status != null) {
            List<Long> longIds = ids.stream().map(Number::longValue).toList();
            announcementService.batchUpdateStatus(longIds, status);
        }
        return Result.success();
    }
}
