package com.blog.api.portal;

import com.blog.api.security.CspViolationService;
import com.blog.common.result.Result;
import com.blog.common.util.IpUtil;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CSP上报", description = "浏览器 CSP 违规上报接收")
@RestController
@RequestMapping("/api/security/csp")
@RequiredArgsConstructor
public class CspReportController {

    private final CspViolationService cspViolationService;

    @Operation(summary = "接收 CSP 违规上报")
    @PostMapping(value = "/report", consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/csp-report",
            "application/reports+json",
            MediaType.TEXT_PLAIN_VALUE
    })
    @RateLimit(key = "cspReport", capacity = 120, seconds = 60)
    public Result<Void> report(@RequestBody(required = false) String payload,
                               HttpServletRequest request) {
        cspViolationService.record(
                payload,
                IpUtil.getClientIp(request),
                request.getHeader("User-Agent")
        );
        return Result.success();
    }
}
