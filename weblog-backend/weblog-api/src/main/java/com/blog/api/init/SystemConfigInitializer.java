package com.blog.api.init;

import com.blog.system.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 系统配置初始化 - 确保必要的配置项存在
 */
@Component
public class SystemConfigInitializer {

    private final SystemConfigService systemConfigService;

    public SystemConfigInitializer(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @PostConstruct
    public void init() {
        systemConfigService.createIfAbsent("site_name", "Weblog", "站点名称");
        systemConfigService.createIfAbsent("site_description", "记录经验、分享洞察、连接有价值的内容。", "站点描述");
        systemConfigService.createIfAbsent("comment_audit_enabled", "true", "评论审核开关（true=开启审核）");
        systemConfigService.createIfAbsent("ad_apply_enabled", "false", "广告申请入口开关（true=开放申请）");
        systemConfigService.createIfAbsent(
                "ad_price_rules",
                "[{\"position\":\"home_left\",\"durationDays\":7,\"price\":299},{\"position\":\"home_left\",\"durationDays\":30,\"price\":1099},{\"position\":\"home_left\",\"durationDays\":90,\"price\":2999},{\"position\":\"home_left\",\"durationDays\":180,\"price\":5399},{\"position\":\"post_top\",\"durationDays\":7,\"price\":239},{\"position\":\"post_top\",\"durationDays\":30,\"price\":899},{\"position\":\"post_top\",\"durationDays\":90,\"price\":2499},{\"position\":\"post_top\",\"durationDays\":180,\"price\":4499},{\"position\":\"post_bottom\",\"durationDays\":7,\"price\":179},{\"position\":\"post_bottom\",\"durationDays\":30,\"price\":699},{\"position\":\"post_bottom\",\"durationDays\":90,\"price\":1899},{\"position\":\"post_bottom\",\"durationDays\":180,\"price\":3499},{\"position\":\"post_list_card\",\"durationDays\":7,\"price\":129},{\"position\":\"post_list_card\",\"durationDays\":30,\"price\":499},{\"position\":\"post_list_card\",\"durationDays\":90,\"price\":1399},{\"position\":\"post_list_card\",\"durationDays\":180,\"price\":2599}]",
                "广告位时效价格规则(JSON)");
        systemConfigService.createIfAbsent("ad_review_reasons", "{}", "广告审核拒绝原因映射(JSON)");
        systemConfigService.createIfAbsent("ad_apply_pit_ids", "[]", "广告申请坑位广告ID列表(JSON)");
        systemConfigService.createIfAbsent("security_alert_enabled", "false", "安全告警邮件开关（true=开启）");
        systemConfigService.createIfAbsent("security_alert_emails", "", "安全告警收件人（逗号分隔邮箱）");
    }
}
