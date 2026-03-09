package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.dto.*;
import com.blog.content.entity.Topic;
import com.blog.content.service.TopicService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;

/**
 * 管理端 - 专题管理
 */
@Tag(name = "管理端-专题管理", description = "专题 CRUD、目录管理、批量操作")
@RestController
@RequestMapping("/api/admin/topic")
@RequiredArgsConstructor
public class AdminTopicController {

    private final TopicService topicService;

    // ========== 专题 CRUD ==========

    @Operation(summary = "分页查询专题列表")
    @GetMapping
    public Result<Map<String, Object>> page(TopicPageReqVO req) {
        IPage<TopicRespVO> page = topicService.getTopicPage(
                req.getPageNum(), req.getPageSize(),
                req.getKeyword(), req.getIsPublish(), req.getIsTop());
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "创建专题")
    @PostMapping
    @AuditLog(module = "专题管理", operation = "CREATE", description = "创建专题")
    public Result<Topic> create(@Valid @RequestBody SaveTopicReqVO req) {
        StpUtil.checkRole("admin");
        return Result.success(topicService.createTopic(req));
    }

    @Operation(summary = "更新专题")
    @PutMapping("/{id}")
    @AuditLog(module = "专题管理", operation = "UPDATE", description = "更新专题")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SaveTopicReqVO req) {
        StpUtil.checkRole("admin");
        topicService.updateTopic(id, req);
        return Result.success();
    }

    @Operation(summary = "删除专题")
    @DeleteMapping("/{id}")
    @AuditLog(module = "专题管理", operation = "DELETE", description = "删除专题")
    public Result<Void> delete(@PathVariable Long id) {
        StpUtil.checkRole("admin");
        topicService.deleteTopic(id);
        return Result.success();
    }

    // ========== 状态切换 ==========

    @Operation(summary = "切换置顶状态")
    @PutMapping("/{id}/toggle-top")
    public Result<Void> toggleTop(@PathVariable Long id) {
        StpUtil.checkRole("admin");
        topicService.toggleTop(id);
        return Result.success();
    }

    @Operation(summary = "切换发布状态")
    @PutMapping("/{id}/toggle-publish")
    public Result<Void> togglePublish(@PathVariable Long id) {
        StpUtil.checkRole("admin");
        topicService.togglePublish(id);
        return Result.success();
    }

    // ========== 批量操作 ==========

    @Operation(summary = "批量删除专题")
    @DeleteMapping("/batch")
    @AuditLog(module = "专题管理", operation = "DELETE", description = "批量删除专题")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        StpUtil.checkRole("admin");
        if (ids != null && !ids.isEmpty()) {
            checkBatchSize(ids.size());
            topicService.batchDelete(ids);
        }
        return Result.success();
    }

    @Operation(summary = "批量设置置顶")
    @PutMapping("/batch/top")
    @AuditLog(module = "专题管理", operation = "UPDATE", description = "批量设置置顶")
    public Result<Void> batchSetTop(@RequestBody Map<String, Object> body) {
        StpUtil.checkRole("admin");
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        Boolean isTop = (Boolean) body.get("isTop");
        if (ids != null && !ids.isEmpty() && isTop != null) {
            checkBatchSize(ids.size());
            List<Long> longIds = ids.stream().map(Number::longValue).toList();
            topicService.batchSetTop(longIds, isTop);
        }
        return Result.success();
    }

    @Operation(summary = "批量设置发布状态")
    @PutMapping("/batch/publish")
    @AuditLog(module = "专题管理", operation = "UPDATE", description = "批量设置发布状态")
    public Result<Void> batchSetPublish(@RequestBody Map<String, Object> body) {
        StpUtil.checkRole("admin");
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        Boolean isPublish = (Boolean) body.get("isPublish");
        if (ids != null && !ids.isEmpty() && isPublish != null) {
            checkBatchSize(ids.size());
            List<Long> longIds = ids.stream().map(Number::longValue).toList();
            topicService.batchSetPublish(longIds, isPublish);
        }
        return Result.success();
    }

    // ========== 目录树 ==========

    @Operation(summary = "获取专题目录树")
    @GetMapping("/{id}/catalogs")
    public Result<List<CatalogNode>> getCatalogs(@PathVariable Long id) {
        return Result.success(topicService.getCatalogs(id));
    }

    @Operation(summary = "保存专题目录树（整体替换）")
    @PutMapping("/{id}/catalogs")
    @AuditLog(module = "专题管理", operation = "UPDATE", description = "保存专题目录树")
    public Result<Void> saveCatalogs(@PathVariable Long id, @RequestBody List<CatalogNode> catalogs) {
        StpUtil.checkRole("admin");
        topicService.saveCatalogs(id, catalogs);
        return Result.success();
    }

    @Operation(summary = "获取专题已引用的文章 ID 列表")
    @GetMapping("/{id}/article-ids")
    public Result<List<Long>> getArticleIds(@PathVariable Long id) {
        return Result.success(topicService.getArticleIds(id));
    }

    // ========== 回收站 ==========

    @Operation(summary = "分页查询回收站专题")
    @GetMapping("/trash")
    public Result<Map<String, Object>> trashPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        var page = topicService.pageDeleted(pageNum, pageSize, keyword);
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()));
    }

    @Operation(summary = "批量恢复专题")
    @PutMapping("/trash/batch-restore")
    @AuditLog(module = "专题管理", operation = "UPDATE", description = "批量恢复专题")
    public Result<Integer> batchRestore(@RequestBody Map<String, List<Long>> body) {
        StpUtil.checkRole("admin");
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) return Result.success(0);
        checkBatchSize(ids.size());
        return Result.success(topicService.batchRestore(ids));
    }

    @Operation(summary = "批量永久删除专题")
    @DeleteMapping("/trash/batch-permanent")
    @AuditLog(module = "专题管理", operation = "DELETE", description = "批量永久删除专题")
    public Result<Integer> batchPermanentDelete(@RequestBody Map<String, List<Long>> body) {
        StpUtil.checkRole("admin");
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) return Result.success(0);
        checkBatchSize(ids.size());
        return Result.success(topicService.batchPermanentDelete(ids));
    }

    @Operation(summary = "清空专题回收站")
    @DeleteMapping("/trash/clear")
    @AuditLog(module = "专题管理", operation = "DELETE", description = "清空专题回收站")
    public Result<Integer> clearTrash() {
        StpUtil.checkRole("admin");
        return Result.success(topicService.clearTrash());
    }

    /** 校验批量操作数量上限 */
    private void checkBatchSize(int size) {
        if (size > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "批量操作数量不能超过 " + MAX_BATCH_SIZE);
        }
    }
}
