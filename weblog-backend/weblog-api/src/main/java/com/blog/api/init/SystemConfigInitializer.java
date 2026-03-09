package com.blog.api.init;

import com.blog.system.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统配置初始化 - 确保必要的配置项存在
 */
@Component
@RequiredArgsConstructor
public class SystemConfigInitializer {

    private final SystemConfigService systemConfigService;

    @PostConstruct
    public void init() {
        systemConfigService.createIfAbsent("comment_audit_enabled", "true", "评论审核开关（true=开启审核）");
    }
}
