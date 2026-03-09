package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.FriendLink;
import com.blog.content.mapper.FriendLinkMapper;
import com.blog.infra.security.util.XssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 友链服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendLinkService {

    private final FriendLinkMapper friendLinkMapper;

    /** 安全URL正则：只允许 http/https 协议 */
    private static final Pattern SAFE_URL_PATTERN =
            Pattern.compile("^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w.,@?^=%&:/~+#\\-]*[\\w@?^=%&/~+#\\-])?$", Pattern.CASE_INSENSITIVE);

    /**
     * 查询所有友链（管理端）
     */
    public List<FriendLink> listAll() {
        return friendLinkMapper.selectList(
                new LambdaQueryWrapper<FriendLink>()
                        .orderByAsc(FriendLink::getSortOrder)
                        .orderByDesc(FriendLink::getCreateTime));
    }

    /**
     * 查询有效友链（用户端展示）
     */
    public List<FriendLink> listActive() {
        return friendLinkMapper.selectList(
                new LambdaQueryWrapper<FriendLink>()
                        .eq(FriendLink::getStatus, "active")
                        .orderByAsc(FriendLink::getSortOrder)
                        .orderByDesc(FriendLink::getCreateTime));
    }

    /**
     * 获取友链详情
     */
    public FriendLink getById(Long id) {
        FriendLink link = friendLinkMapper.selectById(id);
        if (link == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "友链不存在");
        }
        return link;
    }

    /**
     * 创建友链
     */
    @Transactional
    public FriendLink create(String name, String url, String logo, String description, Integer sortOrder) {
        name = XssUtil.cleanText(name);
        url = validateAndCleanUrl(url);
        description = description != null ? XssUtil.cleanText(description) : null;

        FriendLink link = new FriendLink();
        link.setName(name);
        link.setUrl(url);
        link.setLogo(logo);
        link.setDescription(description);
        link.setStatus("active");
        link.setSortOrder(sortOrder != null ? sortOrder : 0);
        friendLinkMapper.insert(link);
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
        link.setName(XssUtil.cleanText(name));
        link.setUrl(validateAndCleanUrl(url));
        link.setLogo(logo);
        link.setDescription(description != null ? XssUtil.cleanText(description) : null);
        if (status != null) {
            link.setStatus(status);
        }
        if (sortOrder != null) {
            link.setSortOrder(sortOrder);
        }
        friendLinkMapper.updateById(link);
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
                .ne(FriendLink::getStatus, "inactive"));
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
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                java.net.URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; WeblogBot/1.0)");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
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

        name = XssUtil.cleanText(name);
        url = validateAndCleanUrl(url);
        description = description != null ? XssUtil.cleanText(description) : null;

        FriendLink link = new FriendLink();
        link.setName(name);
        link.setUrl(url);
        link.setLogo(logo);
        link.setDescription(description);
        link.setStatus("pending");
        link.setSortOrder(0);
        link.setApplicantUserId(userId);
        friendLinkMapper.insert(link);
        log.info("友链申请成功: userId={}, name={}, url={}", userId, name, url);
        return link;
    }

    /**
     * 查询我的友链申请
     */
    public FriendLink getMyLink(Long userId) {
        return friendLinkMapper.selectOne(
            new LambdaQueryWrapper<FriendLink>()
                .eq(FriendLink::getApplicantUserId, userId));
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

        link.setName(XssUtil.cleanText(name));
        link.setUrl(validateAndCleanUrl(url));
        link.setLogo(logo);
        link.setDescription(description != null ? XssUtil.cleanText(description) : null);
        link.setStatus("pending");
        link.setReason(null);
        friendLinkMapper.updateById(link);
        log.info("友链申请更新: userId={}, id={}", userId, link.getId());
        return link;
    }

    /**
     * 审核通过友链申请
     */
    @Transactional
    public FriendLink approveLink(Long id) {
        FriendLink link = getById(id);
        if (!"pending".equals(link.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能审核待审批状态的友链");
        }
        link.setStatus("active");
        link.setReason(null);
        friendLinkMapper.updateById(link);
        log.info("友链审核通过: id={}, name={}", id, link.getName());
        return link;
    }

    /**
     * 拒绝友链申请
     */
    @Transactional
    public FriendLink rejectLink(Long id, String reason) {
        FriendLink link = getById(id);
        if (!"pending".equals(link.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能审核待审批状态的友链");
        }
        link.setStatus("rejected");
        link.setReason(reason);
        friendLinkMapper.updateById(link);
        log.info("友链审核拒绝: id={}, name={}, reason={}", id, link.getName(), reason);
        return link;
    }

    /**
     * 验证URL安全性：禁止 javascript: 等危险协议
     */
    private String validateAndCleanUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL不能为空");
        }
        url = url.trim();

        // 禁止 javascript:、data:、vbscript: 等危险协议
        String lower = url.toLowerCase().replaceAll("\\s+", "");
        if (lower.startsWith("javascript:") || lower.startsWith("data:")
                || lower.startsWith("vbscript:") || lower.startsWith("file:")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不允许的URL协议");
        }

        // 验证URL格式
        if (!SAFE_URL_PATTERN.matcher(url).matches()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "URL格式不正确，仅支持http/https协议");
        }

        return XssUtil.cleanText(url);
    }
}
