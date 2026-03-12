package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.redis.RedisService;
import com.blog.system.dto.LoginResponse;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * GitHub OAuth 登录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubOAuthService {

    private final UserMapper userMapper;
    private final RedisService redisService;

    @Value("${github.oauth.client-id:}")
    private String clientId;

    @Value("${github.oauth.client-secret:}")
    private String clientSecret;

    @Value("${github.oauth.allowed-callbacks:}")
    private String allowedCallbacks;

    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String STATE_KEY_PREFIX = "github:oauth:state:";
    private static final long STATE_EXPIRE_SECONDS = 300;

    /**
     * 生成 GitHub OAuth 授权 URL
     */
    public String getAuthorizationUrl(String redirectUri) {
        ensureOAuthConfigured();
        validateRedirectUri(redirectUri);

        String state = UUID.randomUUID().toString();
        // 存入 Redis，5 分钟有效
        redisService.set(STATE_KEY_PREFIX + state, "1", STATE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&state=" + state
                + "&scope=read:user,user:email";
    }

    /**
     * GitHub OAuth 回调处理
     */
    @Transactional
    public LoginResponse handleCallback(String code, String state, String clientIp) {
        ensureOAuthConfigured();

        // 验证 state 防止 CSRF
        String stateKey = STATE_KEY_PREFIX + state;
        if (!redisService.hasKey(stateKey)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OAuth state 无效或已过期");
        }
        redisService.delete(stateKey);

        // 用 code 换 access_token
        String accessToken = exchangeAccessToken(code);

        // 获取 GitHub 用户信息
        Map<String, Object> githubUser = getGitHubUser(accessToken);
        String githubId = String.valueOf(githubUser.get("id"));
        String nickname = (String) githubUser.get("login");
        String avatar = (String) githubUser.get("avatar_url");
        String email = (String) githubUser.get("email");

        // /user 接口可能拿不到邮箱（用户邮箱未公开），改用 /user/emails 补齐
        if (email == null || email.isBlank()) {
            email = getGitHubUserPrimaryEmail(accessToken);
            log.info("GitHub /user/emails 获取主邮箱: githubId={}, email={}", githubId, email);
        }

        // 1. 先按 githubId 查已绑定账户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getGithubId, githubId));

        if (user == null && email != null && !email.isBlank()) {
            // 2. 如果拿到邮箱，则尝试关联同邮箱已存在账户
            User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            if (existingUser != null) {
                existingUser.setGithubId(githubId);
                if (existingUser.getAvatar() == null || existingUser.getAvatar().isBlank()) {
                    existingUser.setAvatar(avatar);
                }
                user = existingUser;
                log.info("GitHub OAuth 关联已有账号: githubId={}, email={}", githubId, email);
            }
        }

        if (user == null) {
            // 3. 创建新用户
            user = new User();
            user.setGithubId(githubId);
            user.setNickname(nickname);
            user.setAvatar(avatar);
            user.setEmail(email != null && !email.isBlank() ? email : null);
            user.setPassword(""); // OAuth 用户无本地密码
            user.setRole("user");
            user.setStatus("enabled");
            user.setFailedLoginAttempts(0);
            userMapper.insert(user);
            log.info("GitHub OAuth 新用户注册: githubId={}, nickname={}", githubId, nickname);
        }

        // 更新登录信息
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);

        // Sa-Token 登录
        StpUtil.login(user.getId());

        return LoginResponse.builder()
                .success(true)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .needBindEmail(user.getEmail() == null || user.getEmail().isBlank())
                .hasPassword(user.getPassword() != null && !user.getPassword().isBlank())
                .build();
    }

    @SuppressWarnings("unchecked")
    private String exchangeAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_TOKEN_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null || !result.containsKey("access_token")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "GitHub OAuth 授权失败");
        }
        return (String) result.get("access_token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getGitHubUser(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_USER_URL, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        Map<String, Object> user = response.getBody();
        if (user == null || !user.containsKey("id")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "获取 GitHub 用户信息失败");
        }
        return user;
    }

    /**
     * 通过 /user/emails 接口获取用户主邮箱。
     */
    @SuppressWarnings("unchecked")
    private String getGitHubUserPrimaryEmail(String accessToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<List> response = restTemplate.exchange(
                    GITHUB_EMAILS_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            List<Map<String, Object>> emails = response.getBody();
            if (emails == null || emails.isEmpty()) {
                return null;
            }

            // 优先 primary + verified
            for (Map<String, Object> item : emails) {
                Boolean primary = (Boolean) item.get("primary");
                Boolean verified = (Boolean) item.get("verified");
                if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                    return (String) item.get("email");
                }
            }
            // 兜底任意 verified
            for (Map<String, Object> item : emails) {
                if (Boolean.TRUE.equals(item.get("verified"))) {
                    return (String) item.get("email");
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("获取 GitHub 用户邮箱列表失败: {}", e.getMessage());
            return null;
        }
    }

    private void ensureOAuthConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "GitHub OAuth 未配置，请先设置 GITHUB_CLIENT_ID 和 GITHUB_CLIENT_SECRET");
        }
    }

    private void validateRedirectUri(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("invalid scheme");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("invalid host");
            }

            String host = uri.getHost().toLowerCase();

            // 域名白名单验证：未配置时仅允许本地开发回调
            Set<String> allowList = parseAllowedCallbacks();
            if (allowList.isEmpty()) {
                if (!isLocalCallbackHost(host)) {
                    throw new IllegalArgumentException("callback whitelist not configured");
                }
                return;
            }

            boolean allowed = false;
            for (String domain : allowList) {
                if (domain.equals(host) || host.endsWith("." + domain)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new IllegalArgumentException("callback domain not allowed");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 OAuth 回调地址");
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 OAuth 回调地址");
        }
    }

    private Set<String> parseAllowedCallbacks() {
        if (allowedCallbacks == null || allowedCallbacks.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedCallbacks.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private boolean isLocalCallbackHost(String host) {
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
