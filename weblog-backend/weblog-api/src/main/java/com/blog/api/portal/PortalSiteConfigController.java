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
    }
}
