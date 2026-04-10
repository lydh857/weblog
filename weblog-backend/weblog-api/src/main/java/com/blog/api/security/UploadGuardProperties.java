package com.blog.api.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传风控与成本护栏配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.security.upload-guard")
public class UploadGuardProperties {

    /**
     * 是否启用上传风控。
     */
    private boolean enabled = true;

    /**
     * 管理端图片上传限制。
     */
    private SceneLimit adminImage = new SceneLimit(500, 2L * 1024 * 1024 * 1024);

    /**
     * 门户端素材上传限制。
     */
    private SceneLimit portalImage = new SceneLimit(120, 512L * 1024 * 1024);

    /**
     * 用户头像上传限制。
     */
    private SceneLimit avatarImage = new SceneLimit(20, 100L * 1024 * 1024);

    /**
     * 直传签名限制（仅次数）。
     */
    private SceneLimit directSign = new SceneLimit(800, 0L);

    /**
     * 单 IP 每日上传限制（次数 + 字节）。
     */
    private SceneLimit ipDaily = new SceneLimit(600, 2L * 1024 * 1024 * 1024);

    /**
     * 全站每日上传限制（次数 + 字节）。
     */
    private SceneLimit globalDaily = new SceneLimit(8000, 30L * 1024 * 1024 * 1024);

    /**
     * 全站每月上传限制（次数 + 字节）。
     */
    private SceneLimit globalMonthly = new SceneLimit(200000, 500L * 1024 * 1024 * 1024);

    @Data
    public static class SceneLimit {
        private long dailyCountLimit;
        private long dailyBytesLimit;

        public SceneLimit() {
        }

        public SceneLimit(long dailyCountLimit, long dailyBytesLimit) {
            this.dailyCountLimit = dailyCountLimit;
            this.dailyBytesLimit = dailyBytesLimit;
        }
    }
}
