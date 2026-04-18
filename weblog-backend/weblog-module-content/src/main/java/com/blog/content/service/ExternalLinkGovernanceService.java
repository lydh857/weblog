package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.content.entity.LinkDomainPolicy;
import com.blog.content.mapper.LinkDomainPolicyMapper;
import com.blog.content.util.ExternalLinkSeedDomainParser;
import com.blog.infra.security.util.XssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 外链治理服务：统一负责基线风险校验、域名策略决策与待审核落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalLinkGovernanceService {

    private static final Set<String> URL_SHORTENER_HOSTS = Set.of(
            "bit.ly", "t.co", "tinyurl.com", "goo.gl", "is.gd", "ow.ly", "buff.ly", "reurl.cc", "u.nu", "shorturl.at"
    );

    private static final Pattern HIGH_RISK_LINK_PATTERN = Pattern.compile(
            "(?i)(^|[/?#&=_\\-])(login|sign[-_]?in|verify|verification|reset[-_]?password|wallet|payment|bank|credit[-_]?card|otp|2fa|captcha)([/?#&=_\\-]|$)"
    );

    private static final Set<String> VALID_POLICY_STATUS = Set.of("pending", "trusted", "blocked");

    private final LinkDomainPolicyMapper linkDomainPolicyMapper;

    @Value("${blog.security.outbound-link-allowed-domains:}")
    private String outboundLinkSeedDomains;

    public LinkCheckResult evaluateForSubmission(String linkUrl, boolean createPendingIfUnknown) {
        return evaluateInternal(linkUrl, createPendingIfUnknown, false);
    }

    public LinkCheckResult inspectForDisplay(String linkUrl) {
        return evaluateInternal(linkUrl, false, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public LinkDomainPolicy reviewPolicy(Long id,
                                         String nextStatus,
                                         String reviewer,
                                         String reviewReason,
                                         LocalDateTime expireAt) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "策略ID不能为空");
        }
        String normalizedStatus = normalizePolicyStatus(nextStatus);
        LinkDomainPolicy policy = linkDomainPolicyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "域名策略不存在");
        }
        policy.setStatus(normalizedStatus);
        policy.setReviewer(reviewer == null ? null : XssUtil.cleanText(reviewer.trim()));
        policy.setReviewReason(reviewReason == null ? null : XssUtil.cleanText(reviewReason.trim()));
        policy.setExpireAt(expireAt);
        policy.setSource("manual");
        linkDomainPolicyMapper.updateById(policy);
        log.info("外链域名策略更新: id={}, domain={}, status={}", policy.getId(), policy.getDomain(), policy.getStatus());
        return policy;
    }

    public IPage<LinkDomainPolicy> pagePolicies(int pageNum, int pageSize, String status, String keyword) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        LambdaQueryWrapper<LinkDomainPolicy> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isBlank()) {
            wrapper.eq(LinkDomainPolicy::getStatus, normalizePolicyStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(LinkDomainPolicy::getDomain, keyword.trim().toLowerCase(Locale.ROOT));
        }
        wrapper.orderByAsc(LinkDomainPolicy::getStatus)
                .orderByDesc(LinkDomainPolicy::getUpdateTime);
        return linkDomainPolicyMapper.selectPage(new Page<>(pageParams.pageNum(), pageParams.pageSize()), wrapper);
    }

    public String extractNormalizedDomain(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return null;
        }
        String trimmed = linkUrl.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return null;
        }
        URI uri = parseUri(trimmed);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        try {
            return IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private LinkCheckResult evaluateInternal(String linkUrl,
                                             boolean createPendingIfUnknown,
                                             boolean lenient) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return LinkCheckResult.empty();
        }

        String trimmed = linkUrl.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return LinkCheckResult.internal(XssUtil.cleanText(trimmed));
        }

        URI uri = parseUri(trimmed);
        validateScheme(uri);
        validateUserInfo(uri);

        String normalizedHost = normalizeHost(uri);
        validatePublicHost(normalizedHost);
        validateShortener(normalizedHost);
        validateHighRiskPattern(uri);

        PolicyDecision policyDecision = decidePolicy(normalizedHost, createPendingIfUnknown);
        if (policyDecision.blocked()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接域名不在白名单内");
        }

        String cleanedUrl = XssUtil.cleanText(uri.toString());
        if (policyDecision.pending() && !lenient) {
            return LinkCheckResult.pending(cleanedUrl, normalizedHost, policyDecision.reason());
        }
        if (policyDecision.pending()) {
            return LinkCheckResult.pending(cleanedUrl, normalizedHost, policyDecision.reason());
        }
        return LinkCheckResult.trusted(cleanedUrl, normalizedHost, policyDecision.reason());
    }

    private PolicyDecision decidePolicy(String normalizedHost, boolean createPendingIfUnknown) {
        LinkDomainPolicy policy = findBestPolicy(normalizedHost);
        if (policy != null) {
            if ("blocked".equals(policy.getStatus())) {
                return PolicyDecision.blocked("域名已封禁");
            }
            if ("trusted".equals(policy.getStatus()) && !isExpired(policy)) {
                return PolicyDecision.trusted("域名已信任");
            }
            return PolicyDecision.pending("域名待审核");
        }

        if (isInSeedAllowlist(normalizedHost)) {
            return PolicyDecision.trusted("命中静态种子名单");
        }

        if (createPendingIfUnknown) {
            createPendingPolicyIfAbsent(normalizedHost, "auto", "首次提交自动入待审");
        }
        return PolicyDecision.pending("域名待审核");
    }

    private LinkDomainPolicy findBestPolicy(String normalizedHost) {
        Set<String> candidates = buildDomainCandidates(normalizedHost);
        if (candidates.isEmpty()) {
            return null;
        }
        List<LinkDomainPolicy> policies = linkDomainPolicyMapper.selectList(
                new LambdaQueryWrapper<LinkDomainPolicy>()
                        .in(LinkDomainPolicy::getDomain, candidates)
        );
        if (policies == null || policies.isEmpty()) {
            return null;
        }

        LinkDomainPolicy blocked = policies.stream()
                .filter(p -> "blocked".equals(p.getStatus()))
                .findFirst()
                .orElse(null);
        if (blocked != null) {
            return blocked;
        }

        LinkDomainPolicy trusted = policies.stream()
                .filter(p -> "trusted".equals(p.getStatus()))
                .filter(p -> !isExpired(p))
                .findFirst()
                .orElse(null);
        if (trusted != null) {
            return trusted;
        }

        return policies.stream()
                .filter(p -> "pending".equals(p.getStatus()) || isExpired(p))
                .findFirst()
                .orElse(null);
    }

    private void createPendingPolicyIfAbsent(String domain, String source, String reason) {
        LinkDomainPolicy policy = new LinkDomainPolicy();
        policy.setDomain(domain);
        policy.setStatus("pending");
        policy.setSource(source);
        policy.setReviewReason(reason);
        try {
            linkDomainPolicyMapper.insert(policy);
            log.info("外链域名进入待审: domain={}, source={}", domain, source);
        } catch (DuplicateKeyException ignored) {
            // 并发下已存在记录，忽略
        }
    }

    private boolean isExpired(LinkDomainPolicy policy) {
        return policy != null && policy.getExpireAt() != null && policy.getExpireAt().isBefore(LocalDateTime.now());
    }

    private boolean isInSeedAllowlist(String normalizedHost) {
        Set<String> seeds = ExternalLinkSeedDomainParser.parseSeedDomains(outboundLinkSeedDomains);
        if (seeds.isEmpty()) {
            return false;
        }
        for (String seed : seeds) {
            if (normalizedHost.equals(seed) || normalizedHost.endsWith("." + seed)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> buildDomainCandidates(String normalizedHost) {
        if (normalizedHost == null || normalizedHost.isBlank()) {
            return Set.of();
        }
        String[] parts = normalizedHost.split("\\.");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < parts.length - 1; i++) {
            result.add(String.join(".", java.util.Arrays.copyOfRange(parts, i, parts.length)));
        }
        result.add(normalizedHost);
        return result;
    }

    private String normalizePolicyStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "策略状态不能为空");
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!VALID_POLICY_STATUS.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "策略状态仅支持 pending/trusted/blocked");
        }
        return normalized;
    }

    private URI parseUri(String trimmed) {
        try {
            return URI.create(trimmed);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接格式不正确");
        }
    }

    private void validateScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接仅支持 http/https 协议");
        }
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接仅支持 HTTPS 协议");
        }
    }

    private void validateUserInfo(URI uri) {
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接不允许包含用户信息");
        }
    }

    private String normalizeHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接缺少有效主机名");
        }
        try {
            return IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接主机名无效");
        }
    }

    private void validatePublicHost(String normalizedHost) {
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost") || normalizedHost.endsWith(".local")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalizedHost);
            if (addresses.length == 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
            }
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
                }

                if (address instanceof Inet6Address inet6Address) {
                    byte first = inet6Address.getAddress()[0];
                    if ((first & (byte) 0xFE) == (byte) 0xFC) {
                        throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网跳转链接");
        }
    }

    private void validateShortener(String normalizedHost) {
        for (String shortener : URL_SHORTENER_HOSTS) {
            if (normalizedHost.equals(shortener) || normalizedHost.endsWith("." + shortener)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用短链跳转");
            }
        }
    }

    private void validateHighRiskPattern(URI uri) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        String fragment = uri.getRawFragment() == null ? "" : uri.getRawFragment();
        String candidate = (path + "?" + query + "#" + fragment).toLowerCase(Locale.ROOT);
        if (HIGH_RISK_LINK_PATTERN.matcher(candidate).find()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "跳转链接包含高风险路径特征");
        }
    }

    private record PolicyDecision(boolean trusted, boolean pending, boolean blocked, String reason) {
        static PolicyDecision trusted(String reason) {
            return new PolicyDecision(true, false, false, reason);
        }

        static PolicyDecision pending(String reason) {
            return new PolicyDecision(false, true, false, reason);
        }

        static PolicyDecision blocked(String reason) {
            return new PolicyDecision(false, false, true, reason);
        }
    }

    public record LinkCheckResult(String cleanedUrl,
                                  String domain,
                                  String policyStatus,
                                  String reason,
                                  boolean trusted,
                                  boolean pending) {

        public static LinkCheckResult empty() {
            return new LinkCheckResult(null, null, null, null, true, false);
        }

        public static LinkCheckResult internal(String cleanedUrl) {
            return new LinkCheckResult(cleanedUrl, null, "trusted", "站内相对路径", true, false);
        }

        public static LinkCheckResult trusted(String cleanedUrl, String domain, String reason) {
            return new LinkCheckResult(cleanedUrl, domain, "trusted", reason, true, false);
        }

        public static LinkCheckResult pending(String cleanedUrl, String domain, String reason) {
            return new LinkCheckResult(cleanedUrl, domain, "pending", reason, false, true);
        }
    }
}
