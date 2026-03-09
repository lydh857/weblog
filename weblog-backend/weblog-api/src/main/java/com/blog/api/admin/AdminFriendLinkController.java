package com.blog.api.admin;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.FriendLink;
import com.blog.content.service.FriendLinkService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;

/**
 * 管理端友链接口
 */
@Tag(name = "管理端-友链管理", description = "友链CRUD")
@RestController
@RequestMapping("/api/admin/friend-link")
@RequiredArgsConstructor
public class AdminFriendLinkController {

    private final FriendLinkService friendLinkService;
    private static final Set<String> VALID_LINK_STATUSES = Set.of("active", "inactive", "dead", "pending", "rejected");

    @Operation(summary = "获取所有友链")
    @GetMapping
    public Result<List<FriendLink>> listAll() {
        return Result.success(friendLinkService.listAll());
    }

    @Operation(summary = "获取友链详情")
    @GetMapping("/{id}")
    public Result<FriendLink> getById(@PathVariable Long id) {
        return Result.success(friendLinkService.getById(id));
    }

    @Operation(summary = "创建友链")
    @PostMapping
    @AuditLog(module = "友链管理", operation = "CREATE", description = "创建友链")
    public Result<FriendLink> create(@RequestBody FriendLinkRequest req) {
        return Result.success(friendLinkService.create(
                req.getName(), req.getUrl(), req.getLogo(), req.getDescription(), req.getSortOrder()));
    }

    @Operation(summary = "更新友链")
    @PutMapping("/{id}")
    @AuditLog(module = "友链管理", operation = "UPDATE", description = "更新友链")
    public Result<FriendLink> update(@PathVariable Long id, @RequestBody FriendLinkRequest req) {
        return Result.success(friendLinkService.update(
                id, req.getName(), req.getUrl(), req.getLogo(), req.getDescription(),
                req.getStatus(), req.getSortOrder()));
    }

    @Operation(summary = "删除友链")
    @DeleteMapping("/{id}")
    @AuditLog(module = "友链管理", operation = "DELETE", description = "删除友链")
    public Result<Void> delete(@PathVariable Long id) {
        friendLinkService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除友链")
    @DeleteMapping("/batch")
    @AuditLog(module = "友链管理", operation = "DELETE", description = "批量删除友链")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            if (ids.size() > MAX_BATCH_SIZE) {
                throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
            }
            friendLinkService.batchDelete(ids);
        }
        return Result.success();
    }

    @Operation(summary = "批量更新友链状态")
    @PutMapping("/batch/status")
    @AuditLog(module = "友链管理", operation = "UPDATE", description = "批量更新友链状态")
    public Result<Void> batchUpdateStatus(@RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        String status = (String) body.get("status");
        if (status != null && !VALID_LINK_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: active, inactive, dead");
        }
        if (ids != null && !ids.isEmpty() && status != null) {
            friendLinkService.batchUpdateStatus(
                ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList()),
                status);
        }
        return Result.success();
    }

    @Operation(summary = "手动检测友链可达性")
    @PostMapping("/check")
    @AuditLog(module = "友链管理", operation = "UPDATE", description = "手动检测友链可达性")
    public Result<Integer> checkLinks() {
        int changed = friendLinkService.checkAllLinks();
        return Result.success(changed);
    }

    @Operation(summary = "审核通过友链申请")
    @PutMapping("/{id}/approve")
    @AuditLog(module = "友链管理", operation = "UPDATE", description = "审核通过友链申请")
    public Result<FriendLink> approve(@PathVariable Long id) {
        return Result.success(friendLinkService.approveLink(id));
    }

    @Operation(summary = "拒绝友链申请")
    @PutMapping("/{id}/reject")
    @AuditLog(module = "友链管理", operation = "UPDATE", description = "拒绝友链申请")
    public Result<FriendLink> reject(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return Result.success(friendLinkService.rejectLink(id, reason));
    }

    @Data
    public static class FriendLinkRequest {
        @NotBlank(message = "网站名称不能为空")
        @Size(max = 50, message = "网站名称最长50字")
        private String name;

        @NotBlank(message = "网站链接不能为空")
        @Size(max = 200, message = "网站链接最长200字")
        private String url;

        @Size(max = 500, message = "Logo URL最长500字")
        private String logo;

        @Size(max = 200, message = "描述最长200字")
        private String description;

        private String status;
        private Integer sortOrder;
    }
}
