package com.blog.api.portal;

import com.blog.common.result.Result;
import com.blog.common.util.IpUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.dto.LoginResponse;
import com.blog.system.service.GitHubOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * GitHub OAuth 登录接口
 */
@Tag(name = "用户端-GitHub OAuth", description = "GitHub 第三方登录")
@RestController
@RequestMapping("/api/portal/oauth/github")
@RequiredArgsConstructor
public class GitHubOAuthController {

    private final GitHubOAuthService gitHubOAuthService;

    @Operation(summary = "获取 GitHub 授权 URL", description = "生成 GitHub OAuth 授权链接，前端跳转到该链接进行授权")
    @GetMapping("/authorize")
    public Result<String> authorize(@Parameter(description = "OAuth 回调地址") @RequestParam String redirectUri) {
        return Result.success(gitHubOAuthService.getAuthorizationUrl(redirectUri));
    }

    @Operation(summary = "GitHub OAuth 回调", description = "GitHub 授权后回调，用 code 换取 token 并登录")
    @PostMapping("/callback")
    @AuditLog(module = "认证", operation = "LOGIN", description = "GitHub OAuth 登录")
    public Result<LoginResponse> callback(@RequestParam String code,
                                          @RequestParam String state,
                                          HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        LoginResponse response = gitHubOAuthService.handleCallback(code, state, clientIp);
        return Result.success(response);
    }
}
