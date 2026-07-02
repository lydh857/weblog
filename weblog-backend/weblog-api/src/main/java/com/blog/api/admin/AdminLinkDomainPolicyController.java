package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.content.entity.LinkDomainPolicy;
import com.blog.content.service.ExternalLinkGovernanceService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端 - 外链域名治理
 */
@Tag(name = "管理端-外链域名治理", description = "外链域名策略管理")
@RestController
@RequestMapping("/api/admin/link-domain-policy")
@RequiredArgsConstructor
public class AdminLinkDomainPolicyController {

    private final ExternalLinkGovernanceService externalLinkGovernanceService;

    @Operation(summary = "分页查询域名策略")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        IPage<LinkDomainPolicy> page = externalLinkGovernanceService.pagePolicies(pageNum, pageSize, status, keyword);
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "current", page.getCurrent(),
                "pages", page.getPages()
        ));
    }

    @Operation(summary = "通过域名策略（trusted）")
    @PutMapping("/{id}/trust")
    @AuditLog(module = "外链域名治理", operation = "UPDATE", description = "通过域名策略")
    public Result<LinkDomainPolicy> trust(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        ReviewRequest safeBody = body == null ? new ReviewRequest() : body;
        LinkDomainPolicy policy = externalLinkGovernanceService.reviewPolicy(
                id,
                "trusted",
                safeBody.getReviewer(),
                safeBody.getReviewReason(),
                safeBody.getExpireAt()
        );
        return Result.success(policy);
    }

    @Operation(summary = "封禁域名策略（blocked）")
    @PutMapping("/{id}/block")
    @AuditLog(module = "外链域名治理", operation = "UPDATE", description = "封禁域名策略")
    public Result<LinkDomainPolicy> block(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        ReviewRequest safeBody = body == null ? new ReviewRequest() : body;
        LinkDomainPolicy policy = externalLinkGovernanceService.reviewPolicy(
                id,
                "blocked",
                safeBody.getReviewer(),
                safeBody.getReviewReason(),
                safeBody.getExpireAt()
        );
        return Result.success(policy);
    }

    @Operation(summary = "解除封禁（回到 pending）")
    @PutMapping("/{id}/unblock")
    @AuditLog(module = "外链域名治理", operation = "UPDATE", description = "解除封禁")
    public Result<LinkDomainPolicy> unblock(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body) {
        ReviewRequest safeBody = body == null ? new ReviewRequest() : body;
        LinkDomainPolicy policy = externalLinkGovernanceService.reviewPolicy(
                id,
                "pending",
                safeBody.getReviewer(),
                safeBody.getReviewReason(),
                safeBody.getExpireAt()
        );
        return Result.success(policy);
    }

    @Operation(summary = "更新审核备注")
    @PutMapping("/{id}/note")
    @AuditLog(module = "外链域名治理", operation = "UPDATE", description = "更新域名策略备注")
    public Result<LinkDomainPolicy> updateNote(@PathVariable Long id, @RequestBody ReviewRequest body) {
        LinkDomainPolicy policy = externalLinkGovernanceService.reviewPolicy(
                id,
                body.getStatus() == null ? "pending" : body.getStatus(),
                body.getReviewer(),
                body.getReviewReason(),
                body.getExpireAt()
        );
        return Result.success(policy);
    }

    @Data
    public static class ReviewRequest {
        private String status;
        private String reviewer;
        private String reviewReason;

        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime expireAt;
    }
}
