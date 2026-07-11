package com.blog.api.admin;

import com.blog.api.service.AdminCommentService;
import com.blog.common.result.Result;
import com.blog.infra.security.audit.AuditLog;
import com.blog.interaction.dto.BatchStatusReqVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 - 评论管理
 */
@Tag(name = "管理端-评论管理", description = "评论审核、置顶、删除")
@RestController
@RequestMapping("/api/admin/comment")
@RequiredArgsConstructor
public class AdminCommentController {

  private final AdminCommentService adminCommentService;

  @Operation(summary = "评论列表（分页）")
  @GetMapping
  public Result<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long postId,
      @RequestParam(required = false) String postTitle,
      @RequestParam(required = false) Boolean isTop) {
    return Result.success(adminCommentService.listComments(pageNum, pageSize, status, postId, postTitle, isTop));
  }

  @Operation(summary = "待审核评论数量")
  @GetMapping("/pending-count")
  public Result<Long> pendingCount() {
    return Result.success(adminCommentService.getPendingCount());
  }

  @Operation(summary = "审核评论")
  @PutMapping("/{commentId}/status")
  @AuditLog(module = "评论管理", operation = "UPDATE", description = "审核评论")
  public Result<Void> updateStatus(@PathVariable Long commentId,
                                    @RequestParam String status,
                                    @RequestParam(required = false) String reason) {
    adminCommentService.updateStatus(commentId, status, reason);
    return Result.success();
  }

  @Operation(summary = "置顶/取消置顶评论")
  @PutMapping("/{commentId}/top")
  @AuditLog(module = "评论管理", operation = "UPDATE", description = "置顶评论")
  public Result<Void> toggleTop(@PathVariable Long commentId) {
    adminCommentService.toggleTop(commentId);
    return Result.success();
  }

  @Operation(summary = "删除评论")
  @DeleteMapping("/{commentId}")
  @AuditLog(module = "评论管理", operation = "DELETE", description = "删除评论")
  public Result<Void> delete(@PathVariable Long commentId) {
    adminCommentService.deleteComment(commentId);
    return Result.success();
  }

  @Operation(summary = "批量删除评论")
  @DeleteMapping("/batch")
  @AuditLog(module = "评论管理", operation = "DELETE", description = "批量删除评论")
  public Result<Void> batchDelete(@RequestBody List<Long> ids) {
    adminCommentService.batchDelete(ids);
    return Result.success();
  }

  @Operation(summary = "批量审核评论")
  @PutMapping("/batch/status")
  @AuditLog(module = "评论管理", operation = "UPDATE", description = "批量审核评论")
  public Result<Void> batchUpdateStatus(@Valid @RequestBody BatchStatusReqVO req) {
    adminCommentService.batchUpdateStatus(req.getIds(), req.getStatus(), req.getReason());
    return Result.success();
  }
}
