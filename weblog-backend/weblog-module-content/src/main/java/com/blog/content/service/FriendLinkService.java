package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.FriendLink;
import com.blog.content.mapper.FriendLinkMapper;
import com.blog.infra.security.sensitive.SensitiveWordService;
import com.blog.infra.security.util.XssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 友链服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendLinkService {

    private static final int LINK_CHECK_CONNECT_TIMEOUT_MS = 8000;
    private static final int LINK_CHECK_READ_TIMEOUT_MS = 8000;
    private static final int LINK_CHECK_MAX_ATTEMPTS = 3;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_URL_LENGTH = 200;
    private static final int MAX_LOGO_URL_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int MAX_REJECT_REASON_LENGTH = 500;
    private static final Set<String> FRIEND_LINK_PENDING_STATUSES = Set.of("pending", "pending_domain_review");

    private final FriendLinkMapper friendLinkMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ExternalLinkGovernanceService externalLinkGovernanceService;

    /**
     * 查询所有友链（管理端）
     */
    public List<FriendLink> listAll() {
        List<FriendLink> records = friendLinkMapper.selectList(
                new LambdaQueryWrapper<FriendLink>()
                        .orderByAsc(FriendLink::getSortOrder)
                        .orderByDesc(FriendLink::getCreateTime));
        attachLinkPolicyMetadata(records);
        return records;
    }

    /**
     * 查询有效友链（用户端展示）
     */
    public List<FriendLink> listActive() {
        List<FriendLink> records = friendLinkMapper.selectList(
                new LambdaQueryWrapper<FriendLink>()
                        .eq(FriendLink::getStatus, "active")
                        .orderByAsc(FriendLink::getSortOrder)
                        .orderByDesc(FriendLink::getCreateTime));
        attachLinkPolicyMetadata(records);
        return records;
    }

    /**
     * 获取友链详情
     */
    public FriendLink getById(Long id) {
        FriendLink link = friendLinkMapper.selectById(id);
        if (link == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "友链不存在");
        }
        attachLinkPolicyMetadata(link);
        return link;
    }

    /**
     * 创建友链
     */
    @Transactional
    public FriendLink create(String name, String url, String logo, String description, Integer sortOrder) {
        name = sanitizeName(name);
        ExternalLinkGovernanceService.LinkCheckResult linkCheck = externalLinkGovernanceService.evaluateForSubmission(url, true);
        url = linkCheck.cleanedUrl();
        logo = validateAndCleanOptionalUrl(logo);
        description = sanitizeDescription(description);
        validateSensitiveWord("网站名称", name);
        validateSensitiveWord("网站描述", description);

        FriendLink link = new FriendLink();
        link.setName(name);
        link.setUrl(url);
        link.setLogo(logo);
        link.setDescription(description);
        link.setStatus(linkCheck.trusted() ? "active" : "pending_domain_review");
        link.setSortOrder(sortOrder != null ? sortOrder : 0);
        friendLinkMapper.insert(link);
        attachLinkPolicyMetadata(link, linkCheck);
        log.info("友链创建成功: id={}, name={}, url={}", link.getId(), link.getName(), link.getUrl());
        return link;
    }

    /**
     * 更新友链
     */
    @Transactional
    public FriendLink update(Long id, String name, String url, String logo, String description,
                             String status, Integer sortOrder) {
        FriendLink link = getById(id);
        ExternalLinkGovernanceService.LinkCheckResult linkCheck = externalLinkGovernanceService.evaluateForSubmission(url, true);
        link.setName(sanitizeName(name));
        link.setUrl(linkCheck.cleanedUrl());
        link.setLogo(validateAndCleanOptionalUrl(logo));
        link.setDescription(sanitizeDescription(description));
        if (status != null) {
            if ("active".equals(status) && !linkCheck.trusted()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "外链域名未审核通过，不能启用友链");
            }
            link.setStatus(status);
        }
        if (sortOrder != null) {
            link.setSortOrder(sortOrder);
        }
        friendLinkMapper.updateById(link);
        attachLinkPolicyMetadata(link, linkCheck);
        log.info("友链更新成功: id={}", id);
        return link;
    }

    /**
     * 删除友链
     */
    @Transactional
    public void delete(Long id) {
        getById(id);
        friendLinkMapper.deleteById(id);
        log.info("友链删除成功: id={}", id);
    }

    /**
     * 批量删除友链
     */
    @Transactional
    public void batchDelete(List<Long> ids) {
        friendLinkMapper.deleteByIds(ids);
        log.info("友链批量删除: ids={}", ids);
    }

    /**
     * 批量更新友链状态
     */
    @Transactional
    public void batchUpdateStatus(List<Long> ids, String status) {
        friendLinkMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FriendLink>()
                .in(FriendLink::getId, ids)
                .set(FriendLink::getStatus, status));
        log.info("友链批量更新状态: ids={}, status={}", ids, status);
    }

    /**
     * 检测所有非停用友链的可达性
     * active 不可达 → broken，broken 可达 → active
     */
    public int checkAllLinks() {
        List<FriendLink> links = friendLinkMapper.selectList(
            new LambdaQueryWrapper<FriendLink>()
                .in(FriendLink::getStatus, List.of("active", "broken")));
        int changed = 0;
        for (FriendLink link : links) {
            boolean reachable = isUrlReachable(link.getUrl());
            String oldStatus = link.getStatus();
            String newStatus = reachable ? "active" : "broken";
            link.setLastCheckTime(java.time.LocalDateTime.now());
            if (!oldStatus.equals(newStatus)) {
                link.setStatus(newStatus);
                changed++;
                log.info("友链状态变更: id={}, name={}, {} → {}", link.getId(), link.getName(), oldStatus, newStatus);
            }
            friendLinkMapper.updateById(link);
        }
        log.info("友链检测完成: 共{}条, 变更{}条", links.size(), changed);
        return changed;
    }

    /**
     * 检测 URL 是否可达（HEAD 请求，超时 5 秒）
     */
    private boolean isUrlReachable(String url) {
        if (!isSafeExternalUrl(url)) {
            log.warn("友链URL安全校验失败，跳过可达性检测: url={}", url);
            return false;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= LINK_CHECK_MAX_ATTEMPTS; attempt++) {
            try {
                if (isUrlReachableByHeadOrGet(url)) {
                    return true;
                }
            } catch (Exception e) {
                lastError = e;
            }

            if (attempt < LINK_CHECK_MAX_ATTEMPTS) {
                try {
                    long sleepMs = ThreadLocalRandom.current().nextLong(180L, 420L);
                    Thread.sleep(sleepMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        if (lastError != null) {
            log.debug("友链可达性检测失败: url={}, error={}", url, lastError.getMessage());
        }
        return false;
    }

    private boolean isUrlReachableByHeadOrGet(String url) throws Exception {
        int headCode = requestStatusCode(url, "HEAD");
        if (isReachableStatusCode(headCode)) {
            return true;
        }

        // 部分站点不支持 HEAD（如 405/501）或对机器人 HEAD 策略更严格，回退 GET 再判定
        if (headCode == 405 || headCode == 501 || headCode == 403 || headCode == 401) {
            int getCode = requestStatusCode(url, "GET");
            return isReachableStatusCode(getCode);
        }

        return false;
    }

    private int requestStatusCode(String url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(LINK_CHECK_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(LINK_CHECK_READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; WeblogBot/1.0)");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            if ("GET".equals(method)) {
                conn.setRequestProperty("Range", "bytes=0-0");
            }
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private boolean isReachableStatusCode(int code) {
        return (code >= 200 && code < 400) || code == 401 || code == 403;
    }

    /**
     * 用户申请友链
     */
    @Transactional
    public FriendLink applyLink(Long userId, String name, String url, String logo, String description) {
        // 检查是否已申请过
        FriendLink existing = friendLinkMapper.selectOne(
            new LambdaQueryWrapper<FriendLink>()
                .eq(FriendLink::getApplicantUserId, userId));
        if (existing != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "您已申请过友链，请勿重复申请");
        }

        name = sanitizeName(name);
        ExternalLinkGovernanceService.LinkCheckResult linkCheck = externalLinkGovernanceService.evaluateForSubmission(url, true);
        url = linkCheck.cleanedUrl();
        logo = validateAndCleanOptionalUrl(logo);
        description = sanitizeDescription(description);

        FriendLink link = new FriendLink();
        link.setName(name);
        link.setUrl(url);
        link.setLogo(logo);
        link.setDescription(description);
        link.setStatus(linkCheck.pending() ? "pending_domain_review" : "pending");
        link.setSortOrder(0);
        link.setApplicantUserId(userId);
        friendLinkMapper.insert(link);
        attachLinkPolicyMetadata(link, linkCheck);
        log.info("友链申请成功: userId={}, name={}, url={}", userId, name, url);
        return link;
    }

    /**
     * 查询我的友链申请
     */
    public FriendLink getMyLink(Long userId) {
        FriendLink link = friendLinkMapper.selectOne(
            new LambdaQueryWrapper<FriendLink>()
                .eq(FriendLink::getApplicantUserId, userId));
        attachLinkPolicyMetadata(link);
        return link;
    }

    /**
     * 更新我的友链申请（修改后重新进入待审批）
     */
    @Transactional
    public FriendLink updateMyLink(Long userId, String name, String url, String logo, String description) {
        FriendLink link = friendLinkMapper.selectOne(
            new LambdaQueryWrapper<FriendLink>()
                .eq(FriendLink::getApplicantUserId, userId));
        if (link == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到您的友链申请");
        }

        link.setName(sanitizeName(name));
        ExternalLinkGovernanceService.LinkCheckResult linkCheck = externalLinkGovernanceService.evaluateForSubmission(url, true);
        link.setUrl(linkCheck.cleanedUrl());
        link.setLogo(validateAndCleanOptionalUrl(logo));
        link.setDescription(sanitizeDescription(description));
        validateSensitiveWord("网站名称", link.getName());
        validateSensitiveWord("网站描述", link.getDescription());
        link.setStatus(linkCheck.pending() ? "pending_domain_review" : "pending");
        link.setReason(null);
        friendLinkMapper.updateById(link);
        attachLinkPolicyMetadata(link, linkCheck);
        log.info("友链申请更新: userId={}, id={}", userId, link.getId());
        return link;
    }

    /**
     * 审核通过友链申请
     */
    @Transactional
    public FriendLink approveLink(Long id) {
        FriendLink link = getById(id);
        if (!FRIEND_LINK_PENDING_STATUSES.contains(link.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能审核待审批状态的友链");
        }

        ExternalLinkGovernanceService.LinkCheckResult linkCheck = externalLinkGovernanceService.evaluateForSubmission(link.getUrl(), true);
        if (!linkCheck.trusted()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "外链域名未审核通过，不能通过友链申请");
        }

        link.setStatus("active");
        link.setReason(null);
        friendLinkMapper.updateById(link);
        attachLinkPolicyMetadata(link, linkCheck);
        log.info("友链审核通过: id={}, name={}", id, link.getName());
        return link;
    }

    /**
     * 拒绝友链申请
     */
    @Transactional
    public FriendLink rejectLink(Long id, String reason) {
        FriendLink link = getById(id);
        if (!FRIEND_LINK_PENDING_STATUSES.contains(link.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能审核待审批状态的友链");
        }
        link.setStatus("rejected");
        String sanitizedReason = sanitizeRejectReason(reason);
        if (sanitizedReason == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝原因不能为空");
        }
        link.setReason(sanitizedReason);
        friendLinkMapper.updateById(link);
        attachLinkPolicyMetadata(link);
        log.info("友链审核拒绝: id={}, name={}, reason={}", id, link.getName(), sanitizedReason);
        return link;
    }

    private void attachLinkPolicyMetadata(FriendLink link) {
        if (link == null) {
            return;
        }
        try {
            ExternalLinkGovernanceService.LinkCheckResult check = externalLinkGovernanceService.inspectForDisplay(link.getUrl());
            attachLinkPolicyMetadata(link, check);
        } catch (BusinessException ex) {
            link.setLinkDomain(externalLinkGovernanceService.extractNormalizedDomain(link.getUrl()));
            link.setLinkDomainPolicyStatus("blocked");
            link.setLinkDomainPolicyReason(ex.getMessage());
        }
    }

    private void attachLinkPolicyMetadata(FriendLink link, ExternalLinkGovernanceService.LinkCheckResult check) {
        if (link == null || check == null) {
            return;
        }
        link.setLinkDomain(check.domain());
        link.setLinkDomainPolicyStatus(check.policyStatus());
        link.setLinkDomainPolicyReason(check.reason());
    }

    private void attachLinkPolicyMetadata(List<FriendLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        for (FriendLink link : links) {
            attachLinkPolicyMetadata(link);
        }
    }

    /**
     * 验证URL安全性：禁止 javascript: 等危险协议
     */
    private String validateAndCleanUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL不能为空");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL长度不能超过" + MAX_URL_LENGTH + "个字符");
        }
        URI uri = parseAndValidateUrl(trimmed);
        return XssUtil.cleanText(uri.toString());
    }

    private String validateAndCleanOptionalUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LOGO_URL_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Logo URL长度不能超过" + MAX_LOGO_URL_LENGTH + "个字符");
        }
        URI uri = parseAndValidateUrl(trimmed);
        return XssUtil.cleanText(uri.toString());
    }

    private URI parseAndValidateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL格式不正确，仅支持http/https协议");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许的URL协议");
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL不允许包含用户信息");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL缺少有效主机名");
        }

        String normalizedHost;
        try {
            normalizedHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL主机名无效");
        }
        if (isLocalHostName(normalizedHost)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网地址");
        }

        if (!isPublicHost(normalizedHost)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许使用本地或内网地址");
        }

        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL端口无效");
        }

        return uri;
    }

    private boolean isSafeExternalUrl(String url) {
        try {
            parseAndValidateUrl(url);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private boolean isPublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (isPrivateOrLocalAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet6Address inet6Address) {
            byte first = inet6Address.getAddress()[0];
            // fc00::/7 Unique Local Address
            return (first & (byte) 0xFE) == (byte) 0xFC;
        }

        return false;
    }

    private boolean isLocalHostName(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local");
    }

    private String sanitizeName(String name) {
        String cleaned = XssUtil.cleanText(name == null ? "" : name).trim();
        if (cleaned.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "网站名称不能为空");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "网站名称最长" + MAX_NAME_LENGTH + "字");
        }
        return cleaned;
    }

    private String sanitizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String cleaned = XssUtil.cleanText(description).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "描述最长" + MAX_DESCRIPTION_LENGTH + "字");
        }
        return cleaned;
    }

    private String sanitizeRejectReason(String reason) {
        if (reason == null) {
            return null;
        }
        String cleaned = XssUtil.cleanText(reason).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_REJECT_REASON_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "拒绝原因最长" + MAX_REJECT_REASON_LENGTH + "字");
        }
        return cleaned;
    }

    private void validateSensitiveWord(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sensitiveWordService.containsSensitiveWord(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "包含敏感词，请修改后提交");
        }
    }
}
