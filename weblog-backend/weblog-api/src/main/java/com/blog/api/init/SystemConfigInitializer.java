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
        systemConfigService.createIfAbsent("friend_link_apply_enabled", "true", "友链申请入口开关（true=开放申请）");
        systemConfigService.createIfAbsent("ad_apply_enabled", "false", "广告申请入口开关（true=开放申请）");
        systemConfigService.createIfAbsent(
                "ad_price_rules",
                "[{\"position\":\"home_left\",\"durationDays\":7,\"price\":299},{\"position\":\"home_left\",\"durationDays\":30,\"price\":1099},{\"position\":\"home_left\",\"durationDays\":90,\"price\":2999},{\"position\":\"home_left\",\"durationDays\":180,\"price\":5399},{\"position\":\"post_top\",\"durationDays\":7,\"price\":239},{\"position\":\"post_top\",\"durationDays\":30,\"price\":899},{\"position\":\"post_top\",\"durationDays\":90,\"price\":2499},{\"position\":\"post_top\",\"durationDays\":180,\"price\":4499},{\"position\":\"post_bottom\",\"durationDays\":7,\"price\":179},{\"position\":\"post_bottom\",\"durationDays\":30,\"price\":699},{\"position\":\"post_bottom\",\"durationDays\":90,\"price\":1899},{\"position\":\"post_bottom\",\"durationDays\":180,\"price\":3499},{\"position\":\"post_list_card\",\"durationDays\":7,\"price\":129},{\"position\":\"post_list_card\",\"durationDays\":30,\"price\":499},{\"position\":\"post_list_card\",\"durationDays\":90,\"price\":1399},{\"position\":\"post_list_card\",\"durationDays\":180,\"price\":2599}]",
                "广告位时效价格规则(JSON)");
        systemConfigService.createIfAbsent("ad_review_reasons", "{}", "广告审核拒绝原因映射(JSON)");
        systemConfigService.createIfAbsent("ad_apply_pit_ids", "[]", "广告申请坑位广告ID列表(JSON)");
        systemConfigService.createIfAbsent("security_alert_enabled", "false", "安全告警邮件开关（true=开启）");
        systemConfigService.createIfAbsent("security_alert_emails", "", "安全告警收件人（逗号分隔邮箱）");
        systemConfigService.createIfAbsent("daily_read_limit", "3", "未登录用户每日阅读限制");
        systemConfigService.createIfAbsent("login_max_attempts", "5", "登录最大失败次数");
        systemConfigService.createIfAbsent("login_lock_minutes", "30", "登录锁定时间（分钟）");
        systemConfigService.createIfAbsent("login_rate_limit", "5", "登录接口每分钟限流次数");
        systemConfigService.createIfAbsent("upload_max_size_mb", "5", "文件上传最大大小（MB）");
        systemConfigService.createIfAbsent("upload_allowed_types", "jpg,jpeg,png,webp,gif", "允许上传的文件类型");
        systemConfigService.createIfAbsent("comment_max_length", "1000", "评论最大长度");
        systemConfigService.createIfAbsent("comment_rate_limit", "5", "评论每分钟限流次数");
        systemConfigService.createIfAbsent("send_code_rate_limit", "3", "发送验证码每分钟限流次数");
        systemConfigService.createIfAbsent("check_email_rate_limit", "10", "邮箱可用性检查每分钟限流次数");
        systemConfigService.createIfAbsent("forgot_password_rate_limit", "5", "忘记密码每分钟限流次数");
        systemConfigService.createIfAbsent("register_rate_limit", "5", "注册接口每分钟限流次数");
        systemConfigService.createIfAbsent("captcha_generate_rate_limit", "10", "验证码生成/刷新每分钟限流次数");
        systemConfigService.createIfAbsent("captcha_verify_rate_limit", "20", "验证码验证每分钟限流次数");
        systemConfigService.createIfAbsent("upload_rate_limit", "20", "上传接口每5分钟限流次数");
        systemConfigService.createIfAbsent("ad_apply_rate_limit", "5", "广告申请接口每5分钟限流次数");
        systemConfigService.createIfAbsent("friend_link_apply_rate_limit", "5", "友链申请接口每5分钟限流次数");
        systemConfigService.createIfAbsent("friend_link_update_rate_limit", "10", "友链更新接口每5分钟限流次数");
        systemConfigService.createIfAbsent("access_read_rate_limit", "120", "阅读记录接口每分钟限流次数");
        systemConfigService.createIfAbsent("access_unlock_rate_limit", "5", "滑块解锁接口每分钟限流次数");
        systemConfigService.createIfAbsent("interaction_like_toggle_rate_limit", "60", "文章点赞切换每分钟限流次数");
        systemConfigService.createIfAbsent("interaction_like_state_rate_limit", "90", "文章点赞状态设置每分钟限流次数");
        systemConfigService.createIfAbsent("interaction_favorite_toggle_rate_limit", "60", "文章收藏切换每分钟限流次数");
        systemConfigService.createIfAbsent("interaction_favorite_state_rate_limit", "90", "文章收藏状态设置每分钟限流次数");
        systemConfigService.createIfAbsent("interaction_favorite_batch_rate_limit", "20", "批量取消收藏每分钟限流次数");
        systemConfigService.createIfAbsent("comment_delete_rate_limit", "30", "评论删除每分钟限流次数");
        systemConfigService.createIfAbsent("comment_batch_delete_rate_limit", "10", "评论批量删除每分钟限流次数");
        systemConfigService.createIfAbsent("comment_like_toggle_rate_limit", "60", "评论点赞切换每分钟限流次数");
        systemConfigService.createIfAbsent("comment_like_state_rate_limit", "90", "评论点赞状态设置每分钟限流次数");
        systemConfigService.createIfAbsent("ai_writing_rate_limit", "20", "AI 写作接口每分钟限流次数");
        systemConfigService.createIfAbsent("ai_chat_rate_limit", "30", "AI 对话接口每分钟限流次数");
        systemConfigService.createIfAbsent("ai_meta_rate_limit", "20", "AI 元信息接口每分钟限流次数");
        systemConfigService.createIfAbsent("admin_login_rate_limit", "5", "管理端登录接口每分钟限流次数");
        systemConfigService.createIfAbsent("admin_revoke_token_rate_limit", "20", "管理端撤销令牌每分钟限流次数");
        systemConfigService.createIfAbsent("admin_revoke_all_tokens_rate_limit", "5", "管理端撤销全部令牌每分钟限流次数");
        systemConfigService.createIfAbsent("system_config_batch_update_rate_limit", "20", "系统配置批量保存每分钟限流次数");
        systemConfigService.createIfAbsent("ranking_refresh_rate_limit", "5", "手动刷新排行榜每分钟限流次数");
        systemConfigService.createIfAbsent("admin_post_delete_rate_limit", "20", "管理端文章删除每分钟限流次数");
        systemConfigService.createIfAbsent("admin_post_permanent_delete_rate_limit", "10", "管理端文章永久删除/清空回收站每分钟限流次数");
        systemConfigService.createIfAbsent("admin_topic_delete_rate_limit", "20", "管理端专题删除每分钟限流次数");
        systemConfigService.createIfAbsent("admin_topic_permanent_delete_rate_limit", "10", "管理端专题永久删除/清空回收站每分钟限流次数");
        systemConfigService.createIfAbsent("admin_media_delete_rate_limit", "30", "管理端媒体删除每分钟限流次数");
        systemConfigService.createIfAbsent("admin_media_cleanup_rate_limit", "5", "管理端媒体清理未引用资源每分钟限流次数");
        systemConfigService.createIfAbsent("admin_user_status_update_rate_limit", "20", "管理端用户状态变更每分钟限流次数");
        systemConfigService.createIfAbsent("admin_user_reset_password_rate_limit", "10", "管理端重置用户密码每分钟限流次数");
        systemConfigService.createIfAbsent("admin_ad_status_update_rate_limit", "30", "管理端广告状态变更每分钟限流次数");
        systemConfigService.createIfAbsent("admin_ad_delete_rate_limit", "20", "管理端广告删除每分钟限流次数");
        systemConfigService.createIfAbsent("admin_ad_permanent_delete_rate_limit", "10", "管理端广告永久删除/清空回收站每分钟限流次数");
        systemConfigService.createIfAbsent("admin_ad_apply_switch_rate_limit", "20", "管理端广告申请开关更新每分钟限流次数");
        systemConfigService.createIfAbsent("admin_ad_price_rules_rate_limit", "10", "管理端广告价格规则更新每分钟限流次数");
        systemConfigService.createIfAbsent("admin_ad_pit_update_rate_limit", "20", "管理端广告坑位调整每分钟限流次数");
        systemConfigService.createIfAbsent("admin_friend_link_status_update_rate_limit", "30", "管理端友链状态变更/审核每分钟限流次数");
        systemConfigService.createIfAbsent("admin_friend_link_delete_rate_limit", "20", "管理端友链删除每分钟限流次数");
        systemConfigService.createIfAbsent("admin_announcement_status_update_rate_limit", "30", "管理端公告状态变更每分钟限流次数");
        systemConfigService.createIfAbsent("admin_announcement_delete_rate_limit", "20", "管理端公告删除每分钟限流次数");
        systemConfigService.createIfAbsent("user_bind_email_rate_limit", "8", "用户端绑定邮箱每5分钟限流次数");
        systemConfigService.createIfAbsent("user_change_email_rate_limit", "8", "用户端换绑邮箱每5分钟限流次数");
        systemConfigService.createIfAbsent("user_set_password_rate_limit", "8", "用户端设置密码每5分钟限流次数");
        systemConfigService.createIfAbsent("user_reset_password_rate_limit", "8", "用户端重置密码每5分钟限流次数");
        systemConfigService.createIfAbsent("ranking_update_interval", "60", "排行榜更新间隔（分钟）");
        systemConfigService.createIfAbsent("login_log_retention_days", "180", "登录日志保留天数");
        systemConfigService.createIfAbsent("audit_log_retention_days", "180", "审计日志保留天数");
        systemConfigService.createIfAbsent("rate_limit_auto_block_enabled", "false", "接口限流自动封禁开关（true=开启）");
        systemConfigService.createIfAbsent("rate_limit_auto_block_threshold", "20", "接口限流自动封禁触发阈值（窗口内命中次数）");
        systemConfigService.createIfAbsent("rate_limit_auto_block_window_minutes", "10", "接口限流自动封禁统计窗口（分钟）");
        systemConfigService.createIfAbsent("rate_limit_auto_block_minutes", "60", "接口限流自动封禁时长（分钟）");
        systemConfigService.createIfAbsent(
                "rate_limit_auto_block_key_prefixes",
                "register,sendCode,checkEmail,forgotPassword,captchaGenerate,captchaVerify,comment-create,comment-delete,comment-batch-delete,comment-like-toggle,comment-like-state,portal-upload-image,ad-apply,friend-link-apply,friend-link-update,access-read,access-unlock,interaction-like-toggle,interaction-like-state,interaction-favorite-toggle,interaction-favorite-state,interaction-favorite-batch,user-bind-email,user-change-email,user-set-password,user-reset-password,ai-chat,ai-writing,ai-meta,admin-login,admin-post-delete,admin-post-permanent-delete,admin-topic-delete,admin-topic-permanent-delete,admin-media-delete,admin-media-cleanup,admin-user-status-update,admin-user-reset-password,admin-ad-status-update,admin-ad-delete,admin-ad-permanent-delete,admin-ad-apply-switch,admin-ad-price-rules,admin-ad-pit-update,admin-friend-link-status-update,admin-friend-link-delete,admin-announcement-status-update,admin-announcement-delete",
                "接口限流自动封禁适用 key 前缀（逗号分隔）");
    }
}
