package com.blog.api.service;

import com.blog.content.entity.Advertisement;
import com.blog.content.entity.FriendLink;
import com.blog.content.service.AdvertisementService;
import com.blog.content.service.FriendLinkService;
import com.blog.system.entity.User;
import com.blog.system.entity.UserProfileReview;
import com.blog.system.mapper.UserMapper;
import com.blog.system.mapper.UserProfileReviewMapper;
import com.blog.system.service.EmailService;
import com.blog.system.service.SystemConfigService;
import com.blog.api.service.ProfileReviewAvatarResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.blog.common.constant.CommonConstant.PROFILE_REVIEW_PENDING;

/**
 * 审核邮件通知服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewMailActionService {

    private static final String DEFAULT_REVIEW_BASE_URL = "http://localhost:3000";
    private static final int QUICK_BLOCK_MINUTES = 24 * 60;

    private final EmailService emailService;
    private final SystemConfigService systemConfigService;
    private final UserMapper userMapper;
    private final UserProfileReviewMapper userProfileReviewMapper;
    private final FriendLinkService friendLinkService;
    private final ProfileReviewAvatarResourceService profileReviewAvatarResourceService;
    private final AdvertisementService advertisementService;

    public void sendPendingProfileReviewEmail(Long reviewId) {
        try {
            doSendPendingProfileReviewEmail(reviewId);
        } catch (Exception e) {
            log.warn("个人信息待审核提醒邮件发送失败: reviewId={}, error={}", reviewId, e.getMessage());
        }
    }

    private void doSendPendingProfileReviewEmail(Long reviewId) {
        UserProfileReview review = userProfileReviewMapper.selectById(reviewId);
        if (review == null || !PROFILE_REVIEW_PENDING.equals(review.getStatus())) {
            return;
        }
        User user = userMapper.selectById(review.getUserId());
        String adminEmail = getAdminEmail();
        if (adminEmail == null) {
            return;
        }

        String adminReviewUrl = createAdminReviewUrl("/admin/user?tab=reviews&focusId=" + review.getId());
        String html = """
                <div style="max-width:560px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <p style="margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;">有新的个人信息待审核</p>
                  <div style="padding:16px 18px;border:1px solid #e2e8f0;border-radius:12px;margin-bottom:20px;">
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>用户：</b>%s（ID：%d）</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>邮箱：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>新昵称：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>新简介：</b>%s</p>
                    <p style="margin:0;color:#334155;font-size:14px;line-height:1.6;"><b>新头像：</b>%s</p>
                  </div>
                  <p style="margin:0 0 16px;color:#94a3b8;font-size:12px;line-height:1.5;">请进入管理端登录后处理。通过、拒绝、封禁均由管理端统一鉴权和审计。</p>
                  %s
                </div>
                """.formatted(
                escapeHtml(user != null ? user.getNickname() : "未知用户"),
                review.getUserId(),
                escapeHtml(user != null && user.getEmail() != null ? user.getEmail() : "未绑定"),
                escapeHtml(review.getPendingNickname()),
                escapeHtml(review.getPendingBio()),
                avatarHtml(review.getPendingAvatar()),
                buttonHtml("去管理端审核", adminReviewUrl, "#2563eb")
        );
        emailService.sendHtmlMailAsync(adminEmail, "新个人信息待审核", html);
    }

    public void sendProfileReviewResultEmail(Long reviewId, String status, String reason) {
        try {
            doSendProfileReviewResultEmail(reviewId, status, reason);
        } catch (Exception e) {
            log.warn("个人信息审核结果邮件发送失败: reviewId={}, status={}, error={}", reviewId, status, e.getMessage());
        }
    }

    private void doSendProfileReviewResultEmail(Long reviewId, String status, String reason) {
        UserProfileReview review = userProfileReviewMapper.selectById(reviewId);
        if (review == null || (!"approved".equals(status) && !"rejected".equals(status))) {
            return;
        }
        User user = userMapper.selectById(review.getUserId());
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String subject = "approved".equals(status) ? "您的个人信息已通过审核" : "您的个人信息未通过审核";
        String reasonHtml = "rejected".equals(status)
                ? "<div style=\"background:linear-gradient(135deg,#fef2f2,#fff1f2);border:1px solid #fecaca;border-radius:12px;padding:14px 16px;margin:16px 0 0;\"><b>原因：</b>" + escapeHtml(reason) + "</div>"
                : "";
        String html = """
                <div style="max-width:480px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <p style="margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;">%s</p>
                  <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>昵称：</b>%s</p>
                  <p style="margin:0;color:#334155;font-size:14px;line-height:1.6;"><b>简介：</b>%s</p>
                  %s
                </div>
                """.formatted(subject, escapeHtml(review.getPendingNickname()), escapeHtml(review.getPendingBio()), reasonHtml);
        emailService.sendHtmlMailAsync(user.getEmail(), subject, html);
    }

    public void sendPendingFriendLinkReviewEmail(Long linkId) {
        try {
            doSendPendingFriendLinkReviewEmail(linkId);
        } catch (Exception e) {
            log.warn("友链待审核提醒邮件发送失败: linkId={}, error={}", linkId, e.getMessage());
        }
    }

    private void doSendPendingFriendLinkReviewEmail(Long linkId) {
        FriendLink link = friendLinkService.getById(linkId);
        if (link == null || !isPendingFriendLink(link.getStatus())) {
            return;
        }
        User applicant = link.getApplicantUserId() != null ? userMapper.selectById(link.getApplicantUserId()) : null;
        String adminEmail = getAdminEmail();
        if (adminEmail == null) {
            return;
        }

        String adminReviewUrl = createAdminReviewUrl("/admin/friend-link?status=pending&focusId=" + link.getId());
        String html = """
                <div style="max-width:560px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <p style="margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;">有新的友链申请待审核</p>
                  <div style="padding:16px 18px;border:1px solid #e2e8f0;border-radius:12px;margin-bottom:20px;">
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>申请人：</b>%s（ID：%s）</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>邮箱：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>网站名称：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>网站链接：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>Logo：</b>%s</p>
                    <p style="margin:0;color:#334155;font-size:14px;line-height:1.6;"><b>描述：</b>%s</p>
                  </div>
                  <p style="margin:0 0 16px;color:#94a3b8;font-size:12px;line-height:1.5;">请进入管理端登录后处理。通过、拒绝、封禁均由管理端统一鉴权和审计。</p>
                  %s
                </div>
                """.formatted(
                escapeHtml(applicant != null ? applicant.getNickname() : "未知用户"),
                link.getApplicantUserId() == null ? "-" : link.getApplicantUserId().toString(),
                escapeHtml(applicant != null && applicant.getEmail() != null ? applicant.getEmail() : "未绑定"),
                escapeHtml(link.getName()),
                linkHtml(link.getUrl()),
                avatarHtml(link.getLogo()),
                escapeHtml(link.getDescription()),
                buttonHtml("去管理端审核", adminReviewUrl, "#2563eb")
        );
        emailService.sendHtmlMailAsync(adminEmail, "新友链申请待审核", html);
    }

    public void sendFriendLinkReviewResultEmail(Long linkId, String status, String reason) {
        try {
            doSendFriendLinkReviewResultEmail(linkId, status, reason);
        } catch (Exception e) {
            log.warn("友链审核结果邮件发送失败: linkId={}, status={}, error={}", linkId, status, e.getMessage());
        }
    }

    private void doSendFriendLinkReviewResultEmail(Long linkId, String status, String reason) {
        FriendLink link = friendLinkService.getById(linkId);
        if (link == null || link.getApplicantUserId() == null || (!"approved".equals(status) && !"rejected".equals(status))) {
            return;
        }
        User applicant = userMapper.selectById(link.getApplicantUserId());
        if (applicant == null || applicant.getEmail() == null || applicant.getEmail().isBlank()) {
            return;
        }
        String subject = "approved".equals(status) ? "您的友链申请已通过审核" : "您的友链申请未通过审核";
        String reasonHtml = "rejected".equals(status)
                ? "<div style=\"background:linear-gradient(135deg,#fef2f2,#fff1f2);border:1px solid #fecaca;border-radius:12px;padding:14px 16px;margin:16px 0 0;\"><b>原因：</b>" + escapeHtml(reason) + "</div>"
                : "";
        String html = """
                <div style="max-width:480px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <p style="margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;">%s</p>
                  <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>网站名称：</b>%s</p>
                  <p style="margin:0;color:#334155;font-size:14px;line-height:1.6;"><b>网站链接：</b>%s</p>
                  %s
                </div>
                """.formatted(subject, escapeHtml(link.getName()), linkHtml(link.getUrl()), reasonHtml);
        emailService.sendHtmlMailAsync(applicant.getEmail(), subject, html);
    }

    public void sendPendingAdReviewEmail(Long adId) {
        try {
            doSendPendingAdReviewEmail(adId);
        } catch (Exception e) {
            log.warn("广告申请待审核提醒邮件发送失败: adId={}, error={}", adId, e.getMessage());
        }
    }

    private void doSendPendingAdReviewEmail(Long adId) {
        Advertisement ad = advertisementService.getById(adId);
        if (ad == null || !isPendingAd(ad.getStatus())) {
            return;
        }
        User advertiser = ad.getAdvertiserId() != null ? userMapper.selectById(ad.getAdvertiserId()) : null;
        String adminEmail = getAdminEmail();
        if (adminEmail == null) {
            return;
        }
        String adminReviewUrl = createAdminReviewUrl("/admin/advertisement?status=pending&focusId=" + ad.getId());
        String html = """
                <div style="max-width:560px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <p style="margin:0 0 20px;font-size:17px;font-weight:600;color:#0f172a;">有新的广告申请待审核</p>
                  <div style="padding:16px 18px;border:1px solid #e2e8f0;border-radius:12px;margin-bottom:20px;">
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>申请人：</b>%s（ID：%s）</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>邮箱：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>标题：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>位置：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>类型：</b>%s</p>
                    <p style="margin:0 0 8px;color:#334155;font-size:14px;line-height:1.6;"><b>跳转链接：</b>%s</p>
                    <p style="margin:0;color:#334155;font-size:14px;line-height:1.6;"><b>广告内容：</b>%s</p>
                  </div>
                  <p style="margin:0 0 16px;color:#94a3b8;font-size:12px;line-height:1.5;">请进入管理端登录后处理。通过、拒绝、封禁均由管理端统一鉴权和审计。</p>
                  %s
                </div>
                """.formatted(
                escapeHtml(advertiser != null ? advertiser.getNickname() : "未知用户"),
                ad.getAdvertiserId() == null ? "-" : ad.getAdvertiserId().toString(),
                escapeHtml(advertiser != null && advertiser.getEmail() != null ? advertiser.getEmail() : "未绑定"),
                escapeHtml(ad.getTitle()),
                escapeHtml(ad.getPosition()),
                escapeHtml(ad.getType()),
                linkHtml(ad.getLinkUrl()),
                adContentHtml(ad),
                buttonHtml("去管理端审核", adminReviewUrl, "#2563eb")
        );
        emailService.sendHtmlMailAsync(adminEmail, "新广告申请待审核", html);
    }

    private String createAdminReviewUrl(String pathAndQuery) {
        return getReviewBaseUrl() + pathAndQuery;
    }

    private String getReviewBaseUrl() {
        String baseUrl = systemConfigService.getValue("review_action_base_url");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_REVIEW_BASE_URL;
        }
        baseUrl = baseUrl.trim();
        validateReviewBaseUrl(baseUrl);
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void validateReviewBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException("invalid review base url");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("管理端审核链接基础地址配置无效: " + baseUrl);
        }
    }

    private String getAdminEmail() {
        String adminEmail = systemConfigService.getValue("mail_username");
        if (adminEmail == null || adminEmail.isBlank()) {
            return null;
        }
        return adminEmail;
    }

    private boolean isPendingFriendLink(String status) {
        return "pending".equals(status) || "pending_domain_review".equals(status);
    }

    private boolean isPendingAd(String status) {
        return "pending".equals(status) || "pending_domain_review".equals(status);
    }

    private String adContentHtml(Advertisement ad) {
        if (ad == null || ad.getContent() == null || ad.getContent().isBlank()) {
            return "无";
        }
        if ("image".equals(ad.getType())) {
            return avatarHtml(ad.getContent());
        }
        String content = ad.getContent();
        String preview = content.length() > 300 ? content.substring(0, 300) + "..." : content;
        return "<div style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:12px 14px;margin:4px 0 0;white-space:pre-wrap;font-size:13px;color:#334155;line-height:1.5;\">"
                + escapeHtml(preview) + "</div>";
    }

    private String avatarHtml(String url) {
        String normalizedUrl = profileReviewAvatarResourceService.normalizeLegacyUploadUrl(url);
        if (normalizedUrl == null || normalizedUrl.isBlank()) {
            return "无";
        }
        return "<a href=\"" + escapeHtml(normalizedUrl) + "\" style=\"color:#2563eb;\">查看图片</a><br/>"
                + "<img src=\"" + escapeHtml(normalizedUrl) + "\" alt=\"待审核图片\" style=\"max-width:96px;max-height:96px;border-radius:10px;margin-top:8px;\">";
    }

    private String linkHtml(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return "<a href=\"" + escapeHtml(url) + "\" style=\"color:#2563eb;\">" + escapeHtml(url) + "</a>";
    }

    private String buttonHtml(String text, String url, String color) {
        return "<a href=\"" + escapeHtml(url) + "\" style=\"display:inline-block;margin:6px 6px 0 0;padding:10px 20px;background:"
                + color + ";color:#fff;text-decoration:none;border-radius:10px;font-size:14px;font-weight:600;\">" + escapeHtml(text) + "</a>";
    }

    private String escapeHtml(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}