package com.blog.api.crawler.auth;

import cn.hutool.core.util.StrUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class CrawlerIntegrationAuthService {

    public static final String CONFIG_CRAWLER_INGEST_ENABLED = "crawler_ingest_enabled";
    public static final String CONFIG_CRAWLER_INTEGRATION_TOKEN = "crawler_integration_token";

    private final SystemConfigService systemConfigService;

    @Value("${IMPORT_TOKEN:}")
    private String importToken;

    public void checkIngestionEnabled() {
        String enabled = systemConfigService.getValue(CONFIG_CRAWLER_INGEST_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "采集入库功能未开启");
        }
    }

    public void checkIntegrationCredential(String crawlerToken, String deviceId) {
        if (StrUtil.isBlank(crawlerToken) || StrUtil.isBlank(deviceId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缺少采集集成鉴权参数");
        }

        String expectedToken = systemConfigService.getValue(CONFIG_CRAWLER_INTEGRATION_TOKEN);
        if (StrUtil.isBlank(expectedToken)) {
            expectedToken = importToken;
        }
        if (StrUtil.isBlank(expectedToken)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "采集集成令牌未配置");
        }

        // 使用常量时间比较，防止时序攻击逐字符猜测 token
        if (!constantTimeEquals(expectedToken, crawlerToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "采集集成令牌无效");
        }
    }

    /**
     * 常量时间字符串比较，防止时序攻击。
     * 无论两个字符串在哪一位首次出现差异，比较时间均相同。
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        byte[] bytesA = a.getBytes(StandardCharsets.UTF_8);
        byte[] bytesB = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(bytesA, bytesB);
    }
}
