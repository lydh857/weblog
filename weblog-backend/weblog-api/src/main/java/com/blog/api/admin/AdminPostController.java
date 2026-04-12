package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.common.util.PageParamUtil;
import com.blog.content.dto.*;
import com.blog.content.service.PostService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;

/**
 * 管理端文章接口
 */
@Tag(name = "管理端-文章管理", description = "文章CRUD")
@RestController
@RequestMapping("/api/admin/post")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    private static final String ADMIN_POST_DELETE_RATE_LIMIT_KEY = "admin_post_delete_rate_limit";
    private static final String ADMIN_POST_PERMANENT_DELETE_RATE_LIMIT_KEY = "admin_post_permanent_delete_rate_limit";

    @Operation(summary = "创建文章")
    @PostMapping
    @AuditLog(module = "文章管理", operation = "CREATE", description = "创建文章")
    public Result<PostVO> create(@Valid @RequestBody PostCreateRequest req) {
        Long authorId = StpUtil.getLoginIdAsLong();
        return Result.success(postService.create(req, authorId));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/{id}")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "更新文章")
    public Result<PostVO> update(@PathVariable Long id,
                                 @Valid @RequestBody PostCreateRequest req) {
        Long operatorId = StpUtil.getLoginIdAsLong();
        return Result.success(postService.update(id, req, operatorId));
    }

    @Operation(summary = "切换文章置顶状态")
    @PutMapping("/{id}/toggle-top")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "切换文章置顶")
    public Result<Void> toggleTop(@PathVariable Long id) {
        Long operatorId = StpUtil.getLoginIdAsLong();
        postService.toggleTop(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "更新文章SEO设置")
    @PutMapping("/{id}/seo")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "更新文章SEO")
    public Result<Void> updateSeo(@PathVariable Long id,
                                  @Valid @RequestBody PostSeoRequest req) {
        Long operatorId = StpUtil.getLoginIdAsLong();
        postService.updateSeo(id, req, operatorId);
        return Result.success();
    }

    @Operation(summary = "自动保存文章")
    @PutMapping("/{id}/auto-save")
    public Result<Void> autoSave(@PathVariable Long id,
                                 @Valid @RequestBody PostAutoSaveRequest req) {
        Long operatorId = StpUtil.getLoginIdAsLong();
        postService.autoSave(id, req, operatorId);
        return Result.success();
    }

    @Operation(summary = "切换文章启用/禁用状态")
    @PutMapping("/{id}/toggle-disabled")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "切换文章启用/禁用")
    public Result<Void> toggleDisabled(@PathVariable Long id) {
        Long operatorId = StpUtil.getLoginIdAsLong();
        postService.toggleDisabled(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "批量发布草稿")
    @PutMapping("/batch-publish")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "批量发布草稿")
    public Result<Integer> batchPublish(@Valid @RequestBody BatchIdsRequest req) {
        checkBatchSize(req.getIds().size());
        Long operatorId = StpUtil.getLoginIdAsLong();
        return Result.success(postService.batchPublish(req.getIds(), operatorId));
    }

    @Operation(summary = "批量撤销定时发布")
    @PutMapping("/batch-cancel-schedule")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "批量撤销定时发布")
    public Result<Integer> batchCancelSchedule(@Valid @RequestBody BatchIdsRequest req) {
        checkBatchSize(req.getIds().size());
        Long operatorId = StpUtil.getLoginIdAsLong();
        return Result.success(postService.batchCancelSchedule(req.getIds(), operatorId));
    }

    @Operation(summary = "批量定时发布")
    @PutMapping("/batch-schedule")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "批量定时发布")
    public Result<Integer> batchSchedule(@Valid @RequestBody BatchScheduleRequest req) {
        checkBatchSize(req.getIds().size());
        Long operatorId = StpUtil.getLoginIdAsLong();
        java.time.LocalDateTime baseTime = java.time.LocalDateTime.parse(req.getScheduledTime(),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return Result.success(postService.batchSchedule(req.getIds(), baseTime, req.getIntervalMinutes(), operatorId));
    }

    @Operation(summary = "批量设置置顶")
    @PutMapping("/batch-top")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "批量设置置顶")
    public Result<Integer> batchSetTop(@Valid @RequestBody BatchSetTopRequest req) {
        checkBatchSize(req.getIds().size());
        Long operatorId = StpUtil.getLoginIdAsLong();
        return Result.success(postService.batchSetTop(req.getIds(), req.getIsTop(), operatorId));
    }

    @Operation(summary = "批量设置禁用状态")
    @PutMapping("/batch-disabled")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "批量设置禁用状态")
    public Result<Integer> batchSetDisabled(@Valid @RequestBody BatchSetDisabledRequest req) {
        checkBatchSize(req.getIds().size());
        Long operatorId = StpUtil.getLoginIdAsLong();
        return Result.success(postService.batchSetDisabled(req.getIds(), req.getIsDisabled(), operatorId));
    }

    @Operation(summary = "批量删除文章")
    @DeleteMapping("/batch")
    @RateLimit(key = "admin-post-delete", capacity = 120, seconds = 60)
    @AuditLog(module = "文章管理", operation = "DELETE", description = "批量删除文章")
    public Result<Integer> batchDelete(@Valid @RequestBody BatchIdsRequest req, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-post-delete",
                ADMIN_POST_DELETE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "批量删除文章过于频繁，请稍后再试"
        );
        checkBatchSize(req.getIds().size());
        Long operatorId = StpUtil.getLoginIdAsLong();
        boolean isAdmin = StpUtil.hasRole("admin");
        return Result.success(postService.batchDelete(req.getIds(), operatorId, isAdmin));
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    @RateLimit(key = "admin-post-delete", capacity = 120, seconds = 60)
    @AuditLog(module = "文章管理", operation = "DELETE", description = "删除文章")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-post-delete",
                ADMIN_POST_DELETE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "删除文章过于频繁，请稍后再试"
        );
        Long operatorId = StpUtil.getLoginIdAsLong();
        boolean isAdmin = StpUtil.hasRole("admin");
        postService.delete(id, operatorId, isAdmin);
        return Result.success();
    }

    @Operation(summary = "分页查询回收站文章")
    @GetMapping("/trash")
    public Result<IPage<PostVO>> trashPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        return Result.success(postService.pageDeleted(pageParams.pageNum(), pageParams.pageSize(), keyword));
    }

    @Operation(summary = "获取文章详情")
    @GetMapping("/{id}")
    public Result<PostVO> getById(@PathVariable Long id) {
        return Result.success(postService.getById(id));
    }

    @Operation(summary = "分页查询文章列表")
    @GetMapping
    public Result<IPage<PostVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isDisabled,
            @RequestParam(required = false) Long tagId) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        return Result.success(postService.page(pageParams.pageNum(), pageParams.pageSize(), categoryId, status, null, keyword, isDisabled, tagId));
    }

    // ========== 回收站 ==========

    @Operation(summary = "批量恢复文章到草稿箱")
    @PutMapping("/trash/batch-restore")
    @AuditLog(module = "文章管理", operation = "UPDATE", description = "批量恢复文章")
    public Result<Integer> batchRestore(@Valid @RequestBody BatchIdsRequest req) {
        checkBatchSize(req.getIds().size());
        return Result.success(postService.batchRestore(req.getIds()));
    }

    @Operation(summary = "批量永久删除文章")
    @DeleteMapping("/trash/batch-permanent")
    @RateLimit(key = "admin-post-permanent-delete", capacity = 120, seconds = 60)
    @AuditLog(module = "文章管理", operation = "DELETE", description = "批量永久删除文章")
    public Result<Integer> batchPermanentDelete(@Valid @RequestBody BatchIdsRequest req, HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-post-permanent-delete",
                ADMIN_POST_PERMANENT_DELETE_RATE_LIMIT_KEY,
                10,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "批量永久删除文章过于频繁，请稍后再试"
        );
        checkBatchSize(req.getIds().size());
        return Result.success(postService.batchPermanentDelete(req.getIds()));
    }

    @Operation(summary = "清空回收站")
    @DeleteMapping("/trash/clear")
    @RateLimit(key = "admin-post-permanent-delete", capacity = 120, seconds = 60)
    @AuditLog(module = "文章管理", operation = "DELETE", description = "清空回收站")
    public Result<Integer> clearTrash(HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-post-permanent-delete",
                ADMIN_POST_PERMANENT_DELETE_RATE_LIMIT_KEY,
                10,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "清空文章回收站过于频繁，请稍后再试"
        );
        return Result.success(postService.clearTrash());
    }

    /** 校验批量操作数量上限 */
    private void checkBatchSize(int size) {
        if (size > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "批量操作数量不能超过 " + MAX_BATCH_SIZE);
        }
    }
}
