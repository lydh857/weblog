package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.content.entity.Announcement;
import com.blog.content.service.AnnouncementService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private static final Set<String> VALID_ANNOUNCEMENT_STATUSES = Set.of("draft", "published", "archived");

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
    @AuditLog(module = "公告管理", operation = "UPDATE", description = "更新公告状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        if (!VALID_ANNOUNCEMENT_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: draft, published, archived");
        }
        announcementService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    @AuditLog(module = "公告管理", operation = "DELETE", description = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除公告")
    @DeleteMapping("/batch")
    @AuditLog(module = "公告管理", operation = "DELETE", description = "批量删除公告")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
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
    @AuditLog(module = "公告管理", operation = "UPDATE", description = "批量更新公告状态")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
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
