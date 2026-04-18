package com.blog.api.portal;

import com.blog.common.result.Result;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端站点基础配置
 */
@Tag(name = "用户端-站点配置", description = "站点名称与站点描述")
@RestController
@RequestMapping("/api/portal/site-config")
public class PortalSiteConfigController {

    private static final String DEFAULT_SITE_NAME = "Weblog";
    private static final String DEFAULT_SITE_DESCRIPTION = "记录经验、分享洞察、连接有价值的内容。";
    private static final String DEFAULT_SITE_FOOTER_NOTICE = "本站内容仅供学习与交流，商业使用请联系原作者授权。";
    private static final String DEFAULT_SITE_FOOTER_COPYRIGHT = "© 2026 zhhhkl. All rights reserved.";
    private static final String DEFAULT_SITE_DISCLAIMER_CONTENT = "1. 本站所有资源文章出自互联网收集整理，本站不参与制作，如果侵犯了您的合法权益，请联系本站我们会及时删除。\n"
            + "2. 本站发布资源来源于互联网，可能存在水印或者引流等信息，请用户擦亮眼睛自行鉴别，做一个有主见和判断力的用户。\n"
            + "3. 本站资源仅供研究、学习交流之用，若使用商业用途，请购买正版授权，否则产生的一切后果将由下载用户自行承担。";

    private final SystemConfigService systemConfigService;

    public PortalSiteConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Operation(summary = "获取站点基础配置")
    @GetMapping
    @RateLimit(key = "portal-site-config", capacity = 120, seconds = 60)
    public Result<SiteConfigVO> getSiteConfig() {
        SiteConfigVO vo = new SiteConfigVO();
        vo.setSiteName(defaultIfBlank(systemConfigService.getValue("site_name"), DEFAULT_SITE_NAME));
        vo.setSiteDescription(defaultIfBlank(systemConfigService.getValue("site_description"), DEFAULT_SITE_DESCRIPTION));
        vo.setSiteFooterNotice(defaultIfBlank(systemConfigService.getValue("site_footer_notice"), DEFAULT_SITE_FOOTER_NOTICE));
        vo.setSiteFooterCopyright(defaultIfBlank(systemConfigService.getValue("site_footer_copyright"), DEFAULT_SITE_FOOTER_COPYRIGHT));
        vo.setSiteDisclaimerContent(defaultIfBlank(systemConfigService.getValue("site_disclaimer_content"), DEFAULT_SITE_DISCLAIMER_CONTENT));
        return Result.success(vo);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    public static class SiteConfigVO {
        private String siteName;
        private String siteDescription;
        private String siteFooterNotice;
        private String siteFooterCopyright;
        private String siteDisclaimerContent;

        public String getSiteName() {
            return siteName;
        }

        public void setSiteName(String siteName) {
            this.siteName = siteName;
        }

        public String getSiteDescription() {
            return siteDescription;
        }

        public void setSiteDescription(String siteDescription) {
            this.siteDescription = siteDescription;
        }

        public String getSiteFooterNotice() {
            return siteFooterNotice;
        }

        public void setSiteFooterNotice(String siteFooterNotice) {
            this.siteFooterNotice = siteFooterNotice;
        }

        public String getSiteFooterCopyright() {
            return siteFooterCopyright;
        }

        public void setSiteFooterCopyright(String siteFooterCopyright) {
            this.siteFooterCopyright = siteFooterCopyright;
        }

        public String getSiteDisclaimerContent() {
            return siteDisclaimerContent;
        }

        public void setSiteDisclaimerContent(String siteDisclaimerContent) {
            this.siteDisclaimerContent = siteDisclaimerContent;
        }
    }
}
